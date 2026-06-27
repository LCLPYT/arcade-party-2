package work.lclpnet.ap2.game.quick_sg

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.loot.LootTable
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.teleportToRandomSpawns
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.game.util.useOldCombat
import work.lclpnet.ap2.game.util.useSurvivalMode
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.util.PvpBehavior
import work.lclpnet.ap2.util.loot.LazyLootContainerManager
import work.lclpnet.ap2.util.loot.VanillaLootTableFiller
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.EntityDamageCallback
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private val ChestsLooted = Stat("chests_looted", 0)

val WORLD_BORDER_DELAY = TimeUnit.MINUTES.toTicks(2)
val WORLD_BORDER_TIME = TimeUnit.MINUTES.toTicks(2)

class QuickSgInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    val mapSchema: QuickSgSchema,
) : EliminationGameInstance(gameHandle, level, map) {

    val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler).also {
        it.setModifySpeedAttribute(false)
    }

    val lootTableKey: ResourceKey<LootTable> = ResourceKey.create(
        Registries.LOOT_TABLE,
        gameHandle.gameInfo.identifier("chests"),
    )

    var mayLoot = false

    private val stats = useFFAStats(winManager, listOf(
        Kills, DamageDealt, ChestsLooted, DistanceMoved
    ))

    private val lootedChests = HashMap<UUID, MutableSet<BlockPos>>()

    init {
        useSurvivalMode()
        useOldCombat()
    }

    override fun prepare() {
        LazyLootContainerManager(
            players(),
            level,
            VanillaLootTableFiller(lootTableKey),
        ) { _, container ->
            container is ChestBlockEntity || container is BarrelBlockEntity
        }.also { it.setup(gameHandle.hooks) }

        useRemainingPlayersDisplay()
        useSmoothDeath()
        disableEliminationMessages()

        trackDistanceMoved(stats)

        movementBlocker.init(gameHandle.hooks)

        players().forEach {
            movementBlocker.disableMovement(it)
        }

        commons().hideNameTags()
    }

    override fun teleportPlayers() {
        val spacing = map.properties.optNumber("spawn-spacing", 16.0).toDouble()

        teleportToRandomSpawns(mapSchema.scanBox!!, mapSchema.scanStarts, spacing)
    }

    override fun eliminate(player: ServerPlayer, source: DamageSource?, customMsg: TranslatedText?) {
        if (isParticipating(player)) {
            val deathMessages = gameHandle.deathMessages
            val normal = customMsg ?: deathMessages.getDeathMessage(player, source)
            val withHealth = source?.let { deathMessages.getDeathMessageWithKillerHealth(player, it) }

            if (withHealth != null) {
                // the killer's remaining health is private intel, only reveal it to the victim
                normal.sendTo(allPlayers().filter { it != player })
                withHealth.sendTo(player)
            } else {
                normal.sendTo(allPlayers())
            }
        }

        super.eliminate(player, source, customMsg)
    }

    override fun onDeath(player: ServerPlayer, attacker: Entity?) {
        if (attacker is ServerPlayer && attacker != player && isParticipating(attacker)) {
            gainKill(attacker, stats)

            attacker.addEffect(MobEffectInstance(MobEffects.REGENERATION, 10.seconds.inWholeTicks.toInt(), 1))
        }

        super.onDeath(player, attacker)
    }

    override fun go() {
        PvpBehavior(gameHandle, level).configure()

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, world, _, hitResult ->
            when {
                !mayLoot || player !is ServerPlayer || !isParticipating(player) -> InteractionResult.FAIL
                else -> {
                    countChestLoot(player, world, hitResult.blockPos)
                    InteractionResult.PASS
                }
            }
        }

        EntityDamageCallback.HOOK.registerWith(hooks) { entity, source, amount ->
            if (entity is ServerPlayer && isParticipating(entity)) {
                val attacker = source.entity as? ServerPlayer

                if (attacker != null && attacker != entity && isParticipating(attacker)) {
                    val applied = amount.coerceAtMost(entity.health)

                    if (applied > 0f) {
                        stats.modify(attacker, DamageDealt) { it + applied }
                    }
                }
            }

            false
        }

        players().forEach {
            movementBlocker.enableMovement(it)
        }

        mayLoot = true

        commons().scheduleWorldBorderShrink(WORLD_BORDER_DELAY, WORLD_BORDER_TIME, 0)

        runEveryTick {
            for (player in players()) {
                updateCompass(player)
            }
        }
    }

    private fun countChestLoot(player: ServerPlayer, world: Level, pos: BlockPos) {
        val blockEntity = world.getBlockEntity(pos)

        if (blockEntity !is ChestBlockEntity && blockEntity !is BarrelBlockEntity) return

        if (lootedChests.getOrPut(player.uuid) { mutableSetOf() }.add(pos.immutable())) {
            stats.increment(player, ChestsLooted)
        }
    }

    private fun updateCompass(player: ServerPlayer) {
        val server = level.server

        if (!player.inventory.contains { it.isOf(Items.COMPASS) }) return

        val closestEnemy = players()
            .filter { it != player }
            .minByOrNull { it.distanceToSqr(player) }

        player.connection.send(ClientboundSetDefaultSpawnPositionPacket(when {
            closestEnemy != null -> LevelData.RespawnData.of(
                level.level.dimension(),
                closestEnemy.blockPosition(),
                0f,
                0f
            )
            else -> server.respawnData
        }))
    }
}

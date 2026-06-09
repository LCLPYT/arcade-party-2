package work.lclpnet.ap2.game.quick_sg

import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.loot.LootTable
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.ext.toTicks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.util.teleportToRandomSpawns
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.util.PvpBehavior
import work.lclpnet.ap2.util.loot.LazyLootContainerManager
import work.lclpnet.ap2.util.loot.VanillaLootTableFiller
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import java.util.concurrent.TimeUnit

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

    override fun go() {
        PvpBehavior(gameHandle, level).configure()

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks) { player, _, _, _ ->
            when {
                !mayLoot || player !is ServerPlayer || !isParticipating(player) -> InteractionResult.FAIL
                else -> InteractionResult.PASS
            }
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

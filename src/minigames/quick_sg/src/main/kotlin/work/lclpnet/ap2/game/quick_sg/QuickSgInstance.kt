package work.lclpnet.ap2.game.quick_sg

import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.level.storage.loot.LootTable
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.ext.toTicks
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.impl.util.world.SpawnFinder
import work.lclpnet.ap2.util.PvpBehavior
import work.lclpnet.ap2.util.loot.LazyLootContainerManager
import work.lclpnet.ap2.util.loot.VanillaLootTableFiller
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.asJavaRandom

const val DEBUG_SPAWNS = false
val WORLD_BORDER_DELAY = TimeUnit.MINUTES.toTicks(2)
val WORLD_BORDER_TIME = TimeUnit.MINUTES.toTicks(2)

class QuickSgInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    val schemaHolder: SchemaHolder<QuickSgSchema> = useSchema(QuickSgSchema::class.java)

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
            world,
            VanillaLootTableFiller(lootTableKey),
        ) { _, container ->
            container is ChestBlockEntity || container is BarrelBlockEntity
        }.also { it.setup(gameHandle.hooks) }

        useRemainingPlayersDisplay()
        useSmoothDeath()

        teleportPlayers()

        movementBlocker.init(gameHandle.hooks)

        players().forEach {
            movementBlocker.disableMovement(it)
        }

        commons().hideNameTags()
    }

    private fun teleportPlayers() {
        val schema = schemaHolder.get()
        val spacing = map.properties.optNumber("spawn-spacing", 16.0).toDouble()

        val finder = SpawnFinder(spacing, commons().debugController())
        val allSpawns = finder.findSpawns(world, schema.scanBox, schema.scanStarts.toSet())
        val spacedSpawns = finder.generateSpacedSpawns(allSpawns, players().count(), Random.asJavaRandom())

        var i = 0

        for (player in players()) {
            val spawn = spacedSpawns[i++]
            val yaw = Random.nextFloat() * 360f

            player.teleport(spawn, yaw)
        }

        if (DEBUG_SPAWNS) {
            commons().debugController().renderer().ifPresent {
                for (pos in allSpawns) {
                    it.marker(pos, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), 0x0000ff)
                }
            }
        }
    }

    override fun go() {
        PvpBehavior(gameHandle, world).configure()

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
        val server = world.server

        if (!player.inventory.contains { it.isOf(Items.COMPASS) }) return

        val closestEnemy = players()
            .filter { it != player }
            .minByOrNull { it.distanceToSqr(player) }

        player.connection.send(ClientboundSetDefaultSpawnPositionPacket(when {
            closestEnemy != null -> LevelData.RespawnData.of(
                world.level.dimension(),
                closestEnemy.blockPosition(),
                0f,
                0f
            )
            else -> server.respawnData
        }))
    }
}

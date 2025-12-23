package work.lclpnet.ap2.game.quick_sg

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.registry.DynamicRegistries
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.storage.loot.LootTable
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.util.loot.LazyLootContainerManager
import work.lclpnet.ap2.util.loot.SimpleLootFiller
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.schema.SchemaHolder
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.impl.util.world.SpawnFinder
import work.lclpnet.ap2.players
import work.lclpnet.ap2.teleport
import work.lclpnet.ap2.util.loot.JsonLootLoader
import work.lclpnet.ap2.util.loot.LootEntry
import work.lclpnet.ap2.util.loot.VanillaLootTableFiller
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.world.BlockModificationHooks
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes
import work.lclpnet.lobby.game.map.GameMap
import java.util.concurrent.CompletableFuture
import kotlin.random.Random
import kotlin.random.asJavaRandom

const val DEBUG_SPAWNS = false

class QuickSgInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle) {

    val schemaHolder: SchemaHolder<QuickSgSchema> = useSchema(QuickSgSchema::class.java)
    val playerMade = mutableSetOf<BlockPos>()

    val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler).also {
        it.setModifySpeedAttribute(false)
    }

    val lootTableKey: ResourceKey<LootTable> = ResourceKey.create(
        Registries.LOOT_TABLE,
        gameHandle.gameInfo.identifier("chests"),
    )

    var lootContainerManager: LazyLootContainerManager? = null
    var mayLoot = false

    init {
        useSurvivalMode()
        useOldCombat()
    }

    override fun prepare() {
        lootContainerManager = LazyLootContainerManager(
            players(),
            world,
            VanillaLootTableFiller(lootTableKey),
        ).also { it.setup(gameHandle.hooks) }

        useRemainingPlayersDisplay()
        useSmoothDeath()

        teleportPlayers()

        movementBlocker.init(gameHandle.hooks)

        players().forEach {
            movementBlocker.disableMovement(it)
        }
    }

    private fun teleportPlayers() {
        val schema = schemaHolder.get()
        val spacing = map.properties.optNumber("spawn-spacing", 16.0).toDouble()

        val finder = SpawnFinder(spacing, commons().debugController())
        val allSpawns = finder.findSpawns(world, schema.scanBox, schema.scanStart)
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
        gameHandle.protect {
            it.allow(
                ProtectionTypes.ALLOW_DAMAGE,
                ProtectionTypes.EXPLOSION,
                ProtectionTypes.USE_BLOCK,
                ProtectionTypes.USE_ITEM_ON_BLOCK,
                ProtectionTypes.CONSUME_FOOD,
                ProtectionTypes.CHARGE_RESPAWN_ANCHOR,
                ProtectionTypes.CRAFT_ITEM,
                ProtectionTypes.DROP_ITEM,
                ProtectionTypes.EAT_CAKE,
                ProtectionTypes.HUNGER,
                ProtectionTypes.ITEM_SCATTER,
                ProtectionTypes.MODIFY_INVENTORY,
                ProtectionTypes.PICKUP_FLUID,
                ProtectionTypes.PICKUP_ITEM,
                ProtectionTypes.PICKUP_PROJECTILE,
                ProtectionTypes.PLACE_BLOCKS,
                ProtectionTypes.BREAK_BLOCKS,
                ProtectionTypes.PLACE_FLUID,
                ProtectionTypes.PRIME_TNT,
                ProtectionTypes.SWAP_HAND_ITEMS,
            )
        }

        // track player made blocks
        registerHook(BlockModificationHooks.BLOCK_PLACED, BlockModificationHooks.BlockModifiedHook { level, pos, entity ->
            if (level == world && entity is ServerPlayer && isParticipating(entity)) {
                playerMade.add(pos.immutable())
            }
        })

        // only allow breaking player made blocks
        registerHook(BlockModificationHooks.BREAK_BLOCK, BlockModificationHooks.BlockModifyHook { level, pos, entity ->
            level != world || entity !is ServerPlayer || !isParticipating(entity) || !playerMade.contains(pos)
        })

        registerHook(PlayerInteractionHooks.USE_BLOCK, UseBlockCallback { player, level, hand, result ->
            when {
                !mayLoot || player !is ServerPlayer || !isParticipating(player) -> InteractionResult.FAIL
                else -> InteractionResult.PASS
            }
        })

        players().forEach {
            movementBlocker.enableMovement(it)
        }

        mayLoot = true
    }
}

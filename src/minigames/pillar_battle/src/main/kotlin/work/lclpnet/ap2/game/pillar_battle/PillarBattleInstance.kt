package work.lclpnet.ap2.game.pillar_battle

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.dimension.end.EnderDragonFight
import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.stats.CommonStats.BlocksPlaced
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.CommonStats.TimeSurvived
import work.lclpnet.ap2.core.type.ApDragonFight
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.impl.util.world.WorldBorderUtil
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.ServerEntityHooks
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

const val RANDOM_ITEM_DELAY_TICKS = 70
private const val BUILD_HEIGHT = 25
private const val BUILD_OUTER_RADIUS = 10
private const val BORDER_WARN_DISTANCE = 2.0
private const val BORDER_WARN_DELAY_MS = 2000L

class PillarBattleInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    private val random = Random()
    private val movementBlocker = SimpleMovementBlocker(gameHandle.scheduler).also {
        it.setModifySpeedAttribute(false)
    }
    private var pillars: PbSetup.PlacementResult? = null
    private val warnings = HashMap<UUID, Warning>()
    private lateinit var border: WorldBorder
    private val stats = createStats(TimeSurvived, DistanceMoved, BlocksPlaced)

    init {
        useSurvivalMode()
        useOldCombat()
    }

    override fun createWorldBootstrap(world: ServerLevel, map: GameMap): CompletableFuture<Void> {
        val setup = PbSetup(world, map, gameHandle.logger)
        return setup.load().thenRun { pillars = setup.placePillars(gameHandle.participants, random) }
    }

    override fun prepare() {
        useRemainingPlayersDisplay()
        useSmoothDeath()

        commons().gameRuleBuilder()
            .set(GameRules.FALL_DAMAGE, true)
            .set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 128)
            .set(GameRules.SPAWN_PHANTOMS, false)
            .set(GameRules.NATURAL_HEALTH_REGENERATION, true)
            .set(GameRules.MOB_GRIEFING, true)
            .set(GameRules.SPAWN_WANDERING_TRADERS, false)
            .set(GameRules.SPAWN_PATROLS, false)
            .set(GameRules.KEEP_INVENTORY, false)

        movementBlocker.init(gameHandle.hooks)

        val pillars = pillars ?: return
        val spawns = pillars.spawns
        val world = level

        for (player in gameHandle.participants) {
            val spawn = spawns[player.uuid]
            if (spawn == null) {
                gameHandle.logger.error("Failed to find spawn for {}", player.scoreboardName)
                continue
            }

            player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), emptySet(), spawn.yaw, spawn.pitch, true)
            movementBlocker.disableMovement(player)
        }

        setupWorldBorder()
    }

    private fun setupWorldBorder() {
        val pillars = pillars ?: return
        val center = pillars.center
        val radius = pillars.radius + BUILD_OUTER_RADIUS + 0.5

        border = WorldBorderUtil.createBorder(center.x + 0.5, center.z + 0.5, radius * 2)
        border.warningBlocks = 0
    }

    override fun go() {
        val translations = gameHandle.translations

        gameHandle.protect { config ->
            config.allowAll()

            for (type in listOf(ProtectionTypes.PLACE_BLOCKS, ProtectionTypes.PLACE_FLUID)) {
                type.disallow(config) { entity, block ->
                    if (entity is ServerPlayer && outOfBounds(block)) {
                        val msg = translations.translateText(entity, "game.ap2.pillar_battle.out_of_bounds")
                            .formatted(ChatFormatting.RED)
                        entity.sendOverlayMessage(msg)
                        ServerPlayerAccess.playSoundToPlayer(entity, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0f, 0.5f)
                        return@disallow true
                    }
                    false
                }
            }
        }

        BlockModificationHooks.BLOCK_PLACED.registerWith(hooks) { _, _, entity ->
            if (entity is ServerPlayer && players().isParticipating(entity)) {
                stats.increment(entity, BlocksPlaced)
            }
        }

        for (player in gameHandle.participants) {
            movementBlocker.enableMovement(player)
        }

        trackDistanceMoved(stats)
        trackSurvivalTime(stats)

        commons().whenBelowCriticalHeight().then { player ->
            player.hurtServer(player.level(), player.damageSources().fellOutOfWorld(), player.health)
        }

        val randomizer = PbRandomizer(random, gameHandle.participants, level.registryAccess())

        runEvery(RANDOM_ITEM_DELAY_TICKS.ticks) {
            randomizer.giveRandomItems()
        }

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks) { entity, _, _ ->
            if (entity is ServerPlayer && entity.foodData.foodLevel >= 20) {
                entity.foodData.addExhaustion(12f)
                entity.foodData.setSaturation(0f)
            }
            true
        }

        handleEnderDragonAi(hooks)

        runEveryTick { warnWorldBorder() }
    }

    private fun handleEnderDragonAi(hooks: HookRegistrar) {
        val pillars = pillars ?: return
        val center = pillars.center

        ServerEntityHooks.ENTITY_LOAD.registerWith(hooks) { entity, _ ->
            if (entity !is EnderDragon) return@registerWith

            val fight = EnderDragonFight(
                false, false, false, Optional.empty(), 0,
                Optional.of(entity.uuid), Optional.of(center), emptyList(), emptyList()
            )
            @Suppress("KotlinConstantConditions")
            (fight as ApDragonFight).`ap2$setTemporary`()
            fight.init(level, random.nextLong(), pillars.center)

            entity.setDragonFight(fight)
            entity.fightOrigin = center
            entity.phaseManager.setPhase(EnderDragonPhase.HOLDING_PATTERN)
        }
    }

    private fun outOfBounds(pos: BlockPos): Boolean {
        val pillars = pillars ?: return true
        val center = pillars.center

        if (pos.y > center.y + BUILD_HEIGHT) return true

        val cx = center.x
        val cz = center.z
        val totalRadius = pillars.radius + BUILD_OUTER_RADIUS
        val x = pos.x
        val z = pos.z

        return x < cx - totalRadius || x > cx + totalRadius || z < cz - totalRadius || z > cz + totalRadius
    }

    private fun warnWorldBorder() {
        val pillars = pillars ?: return
        val translations = gameHandle.translations
        val center = pillars.center
        val cx = center.x.toDouble()
        val cz = center.z.toDouble()
        val totalRadius = pillars.radius + BUILD_OUTER_RADIUS + 0.5

        val realBorder = level.worldBorder

        for (player in gameHandle.participants) {
            val warning = warnings.computeIfAbsent(player.uuid) { Warning() }

            val dx = totalRadius - abs(cx + 0.5 - player.x)
            val dz = totalRadius - abs(cz + 0.5 - player.z)

            if (dx > BORDER_WARN_DISTANCE && dz > BORDER_WARN_DISTANCE) {
                if (warning.warned) {
                    warning.warned = false
                    WorldBorderUtil.init(player, realBorder)

                    if (System.currentTimeMillis() - warning.lastWarning < 62L * 50) {
                        player.sendOverlayMessage(Component.empty())
                    }
                }
                continue
            }

            if (warning.warned) continue

            warning.warned = true
            WorldBorderUtil.init(player, border)

            val timestamp = System.currentTimeMillis()
            if (timestamp - warning.lastWarning < BORDER_WARN_DELAY_MS) continue

            warning.lastWarning = timestamp

            val msg = translations.translateText(player, "game.ap2.pillar_battle.border_warn")
                .styled { it.withColor(0xff0000).withBold(true) }

            player.sendOverlayMessage(msg)
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 0.3f, 0.5f)
        }
    }

    private class Warning {
        var warned = false
        var lastWarning = 0L
    }
}

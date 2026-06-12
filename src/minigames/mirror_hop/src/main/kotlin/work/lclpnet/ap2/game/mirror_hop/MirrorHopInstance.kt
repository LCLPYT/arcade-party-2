package work.lclpnet.ap2.game.mirror_hop

import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.CombinedDataContainer
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.OrderedDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.util.useDataContainer
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.game.util.useTaskDisplay
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.effect.ApEffects
import work.lclpnet.ap2.impl.util.movement.CooldownMovementBlocker
import work.lclpnet.ap2.impl.util.movement.MovementBlocker
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector
import work.lclpnet.gaco.collisions.CollisionDetector
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

private val Falls = Stat("falls", 0, higherIsBetter = false)
private val PlatformsMaterialized = Stat("platforms_materialized", 0)
private val PlatformsBroken = Stat("platforms_broken", 0, higherIsBetter = false)

private fun removeGate(map: GameMap, world: ServerLevel) {
    val gate = MapUtil.readBox(map.requireProperty("gate"))
    val air = Blocks.AIR.defaultBlockState()
    for (pos in gate) {
        world.setBlockAndUpdate(pos, air)
    }
}

class MirrorHopInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {

    private val winnerData = OrderedDataContainer(PlayerRef::create)
    private val scoreData = IntScoreDataContainer(PlayerRef::create)
    override val data = useDataContainer { CombinedDataContainer(listOf(winnerData, scoreData)) }
    private val stats = useFFAStats(winManager, scoreData, CommonStats.IntScore, listOf(
        Falls, PlatformsMaterialized, PlatformsBroken
    ))
    private val collisionDetector: CollisionDetector = ChunkedCollisionDetector()
    private val movementObserver = PlayerMovementObserver(
        collisionDetector,
        gameHandle.participants::isParticipating,
        true,
        0e-5
    )
    private val movementBlocker: MovementBlocker = CooldownMovementBlocker(gameHandle.rootScheduler)
    private lateinit var choices: MirrorHopChoices
    private var progress = -1

    override fun prepare() {
        gameHandle.playerUtil.enableEffect(ApEffects.DARKNESS)

        choices = mirrorHopChoicesFrom(map, gameHandle.logger)
        choices.randomize(Random())
        choices.addColliders(collisionDetector)

        val scoreboardManager = gameHandle.scoreboardManager

        val team = scoreboardManager.createTeam("team")
        team.setSeeFriendlyInvisibles(true)
        team.collisionRule = Team.CollisionRule.NEVER

        scoreboardManager.joinTeam(gameHandle.participants, team)

        useTaskDisplay()
    }

    override fun go() {
        val goal = MapUtil.readBox(map.requireProperty("goal"))

        movementObserver.init(gameHandle.hooks, gameHandle.server)

        movementObserver.whenEntering(goal) { player ->
            if (winManager.gameOver) return@whenEntering
            winnerData.add(player)
            winManager.complete()
        }

        movementObserver.setRegionEnterListener { player, collider ->
            if (collider !is MirrorHopChoices.Platform) return@setRegionEnterListener

            val idx = choices.getChoiceIndex(collider)
            if (idx == -1) return@setRegionEnterListener

            if (idx > progress) {
                commons().addScore(player, 1, scoreData)
                progress = idx
            }

            collisionDetector.remove(collider)

            if (choices.isCorrect(collider, idx)) {
                stats.increment(player, PlatformsMaterialized)
                solidifyPlatform(collider)
            } else {
                stats.increment(player, PlatformsBroken)
                breakPlatform(collider)
            }
        }

        movementBlocker.init(gameHandle.hooks)

        commons().whenBelowCriticalHeight().then(::playerFell)

        removeGate(map, level)
    }

    private fun playerFell(player: ServerPlayer) {
        stats.increment(player, Falls)

        gameHandle.worldFacade.teleport(player)

        val ticks = Ticks.seconds(4)
        movementBlocker.disableMovement(player, ticks)

        val invisibility = MobEffectInstance(MobEffects.INVISIBILITY, ticks, 1, false, false, false)
        player.addEffect(invisibility)
    }

    private fun solidifyPlatform(platform: MirrorHopChoices.Platform) {
        val ground = platform.ground
        val solid = MapUtil.readBlockState(map.requireProperty("solid_material"))

        for (pos in ground) {
            level.setBlockAndUpdate(pos, solid)
        }

        val center = ground.center
        val x = center.x; val y = center.y + 1; val z = center.z

        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.3f, 1f)
        level.sendParticles(ParticleTypes.EGG_CRACK, x, y, z, 10, 0.8, 0.5, 0.8, 0.1)
    }

    private fun breakPlatform(platform: MirrorHopChoices.Platform) {
        val ground = platform.ground

        val air = Blocks.AIR.defaultBlockState()
        for (pos in ground) {
            level.setBlockAndUpdate(pos.below(), air)
        }

        val center = ground.center
        val x = center.x; val y = center.y; val z = center.z

        level.playSound(null, x, y + 1, z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 0.3f, 0f)

        val particleEffect = BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_CONCRETE_POWDER.defaultBlockState())
        level.sendParticles(particleEffect, x, y, z, 10, 0.8, 0.5, 0.8, 0.5)
    }
}

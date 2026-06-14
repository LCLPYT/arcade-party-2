package work.lclpnet.ap2.game.splashy_dropper

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Team
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.util.createStats
import work.lclpnet.ap2.game.util.createTimer
import work.lclpnet.ap2.game.util.finaleCompatibleScoreContainer
import work.lclpnet.ap2.impl.util.handler.Visibility
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler
import work.lclpnet.ap2.impl.util.handler.VisibilityManager
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.combatctl.impl.CombatStyles
import work.lclpnet.gaco.collisions.util.GroundDetector
import work.lclpnet.game.map.GameMap
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

val DURATION = 60.seconds

val HitSmall = Stat("hit_small", 0)
val HitMedium = Stat("hit_medium", 0)
val HitLarge = Stat("hit_large", 0)
val Missed = Stat("missed", 0)

class SplashyDropperInstance(gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : FFAGameInstance(gameHandle, level, map) {

    override val data = finaleCompatibleScoreContainer(gameHandle, PlayerRef::create)
    private val stats = createStats(winManager, data, HitSmall, HitMedium, HitLarge, Missed)
    private val random = Random()
    private val blocksBelow = ArrayList<BlockPos>()
    private val movementBlocker = SimpleMovementBlocker(gameHandle.rootScheduler).also {
        it.setModifySpeedAttribute(false)
    }
    private lateinit var worldScanner: BfsWorldScanner
    private lateinit var groundDetector: GroundDetector
    private var minSpawnY = 70.0

    init {
        gameHandle.playerUtil.setDefaultCombatStyle(CombatStyles.CLASSIC.andThen(
            { it.isDisableOldBobbing = true },
                { _ -> }
        ))
    }

    override fun prepare() {
        setupObjective()
        setupTeam()

        commons().teleportToRandomSpawns(random)

        movementBlocker.init(hooks)

        val adj = SimpleAdjacentBlocks({ pos -> level.getFluidState(pos).isIn(FluidTags.WATER) }, 0)
        worldScanner = BfsWorldScanner(adj)
        groundDetector = GroundDetector(level, 0.35)

        for (player in gameHandle.participants) {
            movementBlocker.disableMovement(player)
        }

        minSpawnY = commons().spawns.minOfOrNull { it.y() } ?: 70.0
    }

    override fun go() {
        val translations = gameHandle.translations
        val subject = translations.translateText(gameHandle.gameInfo.taskKey)

        createTimer(subject, DURATION).whenDone(winManager::complete)

        runEveryTick {
            tick()
        }

        for (player in gameHandle.participants) {
            movementBlocker.enableMovement(player)
        }
    }

    private fun setupTeam() {
        val scoreboardManager = gameHandle.scoreboardManager
        val team = scoreboardManager.createTeam("team")
        team.collisionRule = Team.CollisionRule.NEVER
        scoreboardManager.joinTeam(gameHandle.participants, team)

        val visibility = VisibilityHandler(
            VisibilityManager(team, Visibility.PARTIALLY_VISIBLE),
            gameHandle.translations,
            gameHandle.participants
        )

        visibility.init(hooks)
        visibility.giveItems()
    }

    private fun setupObjective() {
        val objective = gameHandle.scoreboardManager
            .translateObjective("score", "game.ap2.chicken_shooter.points")
            .formatted(ChatFormatting.YELLOW, ChatFormatting.BOLD)

        useScoreboardStatsSync(data, objective)
        objective.setSlot(DisplaySlot.LIST)
        objective.setNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT)

        for (player in PlayerLookup.all(gameHandle.server)) {
            objective.add(player)
        }
    }

    private fun tick() {
        if (winManager.gameOver) return

        outer@ for (player in gameHandle.participants) {
            if (player.y >= minSpawnY - 1) continue

            if (level.getFluidState(player.blockPosition()).isIn(FluidTags.WATER)) {
                onLandInWater(player)
                continue
            }

            blocksBelow.clear()
            groundDetector.collectBlocksBelow(player, blocksBelow)

            for (pos in blocksBelow) {
                val state = level.getBlockState(pos)
                if (state.getCollisionShape(level, pos, CollisionContext.of(player)).isEmpty) continue

                onHitGround(player)
                continue@outer
            }
        }
    }

    private fun onLandInWater(player: ServerPlayer) {
        val count = removeWater(player.blockPosition())
        val score = (4 - sqrt(count.toDouble()).roundToInt()).coerceIn(0, 3)

        commons().addScore(player, score, data)

        val stat = when (score) {
            3 -> HitSmall
            2 -> HitMedium
            1 -> HitLarge
            else -> null
        }
        stat?.let { stats.increment(player, it) }

        val pitch = when (score) {
            2 -> 1.6f
            3 -> 1.8f
            else -> 1.4f
        }

        gameHandle.scheduler.immediate(Runnable {
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, pitch)
        })

        commons().teleportToRandomSpawn(player, random)
    }

    private fun onHitGround(player: ServerPlayer) {
        stats.increment(player, Missed)
        commons().teleportToRandomSpawn(player, random)
        gameHandle.scheduler.immediate(Runnable {
            player.playNotifySound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.PLAYERS, 0.25f, 0.5f)
        })
    }

    private fun removeWater(pos: BlockPos): Int {
        val it = worldScanner.scan(pos)
        var count = 0
        val air = Blocks.AIR.defaultBlockState()

        while (it.hasNext()) {
            level.setBlockAndUpdate(it.next(), air)
            count++
        }

        return count
    }
}

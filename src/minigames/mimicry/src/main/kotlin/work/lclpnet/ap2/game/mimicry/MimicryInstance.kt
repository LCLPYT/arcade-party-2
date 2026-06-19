package work.lclpnet.ap2.game.mimicry

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.StatUnits
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.ext.runAfter
import work.lclpnet.ap2.ext.ticks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.mimicry.data.MimicryManager
import work.lclpnet.ap2.game.mimicry.data.MimicryRoom
import work.lclpnet.ap2.game.mimicry.data.SequencePlayer
import work.lclpnet.ap2.game.util.createTimer
import work.lclpnet.ap2.game.util.useAnnouncer
import work.lclpnet.ap2.game.util.useDataContainer
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.impl.game.PseudoElimination
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.hook.ServerMessageHooks
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times

private const val INITIAL_SEQUENCE_LENGTH = 3
private val PREPARE_TIME = 50.ticks
private val REPLAY_MIN_TIME = 8.seconds
private val REPLAY_MAX_TIME = 30.seconds
private val REPLAY_TIME_PER_NOTE = 1.seconds
private val NEXT_ROUND_DELAY = 4.seconds

val AvgTimeUsage = Stat("avg_time_usage", 0f, unit = StatUnits.Percent, higherIsBetter = false)
val AvgClickTime = Stat("avg_click_time", 0f, unit = StatUnits.Seconds, higherIsBetter = false)

class MimicryInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    result: StackedRoomGenerator.Result<MimicryRoom>,
    buttons: BlockBox
) : FFAGameInstance(gameHandle, level, map) {

    override val data = useDataContainer { refs ->
        IntScoreDataContainer(
            refs,
            Ordering.DESCENDING,
            "completed"
        )
    }
    private val stats = useFFAStats(winManager, data, CommonStats.IntScore, listOf(
        AvgTimeUsage, AvgClickTime
    ))
    private val manager = MimicryManager(gameHandle, result.rooms, buttons, Random(), level, stats, ::onCompleted)
    private val announcer = useAnnouncer()
    private lateinit var pseudoElimination: PseudoElimination
    private lateinit var sequencePlayer: SequencePlayer
    private var timer: BossBarTimer? = null
    private var timerTransaction = 0
    private var phase = Phase.IDLE

    override fun prepare() {
        val world: ServerLevel = level

        pseudoElimination = PseudoElimination(gameHandle, world)

        manager.eachParticipant { player, room -> room.teleport(player, world) }

        ServerMessageHooks.ALLOW_CHAT_MESSAGE.registerWith(gameHandle.hooks) { _, _, _ -> false }
        ServerMessageHooks.ALLOW_COMMAND_MESSAGE.registerWith(gameHandle.hooks) { _, _, _ -> false }
    }

    override fun go() {
        sequencePlayer = SequencePlayer(manager, gameHandle.scheduler, level)

        repeat(INITIAL_SEQUENCE_LENGTH - 1) {
            manager.extendSequence()
        }

        nextSequence()

        PlayerInteractionHooks.USE_BLOCK.registerWith(gameHandle.hooks, ::onUseBlock)
    }

    private fun onUseBlock(player: Player, world: Level, hand: InteractionHand, hitResult: BlockHitResult): InteractionResult {
        if (winManager.gameOver
            || hand != InteractionHand.MAIN_HAND
            || player !is ServerPlayer
            || !gameHandle.participants.isParticipating(player)
            || pseudoElimination.isEliminated(player)) {
            return InteractionResult.PASS
        }

        val pos = getEffectivelyClickedPos(world, hitResult) ?: return InteractionResult.PASS

        if (!manager.onInputButton(player, pos)) {
            return InteractionResult.FAIL
        }

        val msg = gameHandle.translations.translateText(player, "wrong_button")
            .withStyle(ChatFormatting.RED)

        player.sendSystemMessage(msg)

        softEliminate(player)

        return InteractionResult.FAIL
    }

    private fun getEffectivelyClickedPos(world: Level, hitResult: BlockHitResult): BlockPos? {
        val pos = hitResult.blockPos

        if (world.getBlockState(pos).isIn(BlockTags.BUTTONS)) {
            return pos
        }

        val rel = pos.relative(hitResult.direction)

        if (world.getBlockState(rel).isIn(BlockTags.BUTTONS)) {
            return rel
        }

        return null
    }

    @Synchronized
    private fun nextSequence() {
        if (phase != Phase.IDLE || winManager.gameOver) return

        removeTimer()

        announcer.announceSubtitle("attention")

        runAfter(PREPARE_TIME) { playSequence() }
    }

    @Synchronized
    private fun removeTimer() {
        val t = timer ?: return
        timerTransaction++
        t.stop()
        timer = null
    }

    @Synchronized
    private fun playSequence() {
        if (phase != Phase.IDLE || winManager.gameOver) return

        phase = Phase.PLAYING

        manager.reset()
        manager.extendSequence()

        sequencePlayer.setPeriodTicks(10 - manager.sequenceLength() / 2)
        sequencePlayer.play().whenComplete { beginReplay() }
    }

    @Synchronized
    private fun beginReplay() {
        if (phase != Phase.PLAYING || winManager.gameOver) return

        phase = Phase.REPLAY

        announcer.announceSubtitle("repeat")

        manager.replay = true

        val translations = gameHandle.translations
        val subject = translations.translateText(gameHandle.gameInfo.taskKey)

        val replayTime = calcReplayTime()
        manager.beginReplay(replayTime)

        val t = createTimer(subject, replayTime)
        timer = t

        val transaction = timerTransaction

        t.whenDone {
            if (transaction == timerTransaction) {
                endReplayAndEliminate()
            }
        }
    }

    @Synchronized
    private fun endReplayAndEliminate() {
        if (phase != Phase.REPLAY || winManager.gameOver) return

        manager.playersToEliminate.forEach { softEliminate(it) }

        onRoundOver()

        runAfter(NEXT_ROUND_DELAY) { nextSequence() }
    }

    @Synchronized
    private fun onRoundOver() {
        if (phase != Phase.REPLAY) return

        phase = Phase.IDLE

        removeTimer()

        manager.replay = false

        pseudoElimination.commit()
    }

    private fun calcReplayTime(): Duration =
        (manager.sequenceLength() * REPLAY_TIME_PER_NOTE).coerceIn(REPLAY_MIN_TIME, REPLAY_MAX_TIME)

    private fun onCompleted(player: ServerPlayer) {
        commons().addScore(player, 1, data)
        checkRoundComplete()
    }

    private fun onAllCompleted() {
        if (phase != Phase.REPLAY) return

        onRoundOver()

        runAfter(30.ticks) { nextSequence() }
    }

    @Synchronized
    private fun softEliminate(player: ServerPlayer) {
        if (phase != Phase.REPLAY || !pseudoElimination.eliminate(player)) return
        checkRoundComplete()
    }

    @Synchronized
    private fun checkRoundComplete() {
        if (phase != Phase.REPLAY) return

        val notCompleted = manager.playersToEliminate.size

        if (notCompleted == 0) {
            onAllCompleted()
            return
        }

        val remainingReplay = notCompleted - pseudoElimination.size()

        if (remainingReplay <= 0) {
            endReplayAndEliminate()
        }
    }

    private enum class Phase { PLAYING, REPLAY, IDLE }
}

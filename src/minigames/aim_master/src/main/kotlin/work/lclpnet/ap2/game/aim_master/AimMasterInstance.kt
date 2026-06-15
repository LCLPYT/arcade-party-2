package work.lclpnet.ap2.game.aim_master

import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.StatUnits
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.util.useDataContainer
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.game.util.usePlayerDynamicTaskDisplay
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.hook.player.PlayerSwingHandHook
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.SchedulerAction
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import java.util.*
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

const val SCORE_GOAL = 24

private val Clicks = Stat("clicks", 0)
private val Misses = Stat("misses", 0)
private val Accuracy = Stat("accuracy", 0f, unit = StatUnits.Percent)
private val Streak = Stat("streak", 0)
private val AvgAdvanceTime = Stat("avg_advance_time", 0f, unit = StatUnits.Seconds)

class AimMasterInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    val sequence: AimMasterSequence,
    val manager: AimMasterManager,
) : FFAGameInstance(gameHandle, level, map) {

    override val data = useDataContainer(::IntScoreDataContainer)
    private val stats = useFFAStats(winManager, data, CommonStats.IntScore, listOf(
        Clicks, Misses, Accuracy, Streak, AvgAdvanceTime
    ))
    private val currentStreak = HashMap<UUID, Int>()
    private val lastAdvanceMillis = HashMap<UUID, Long>()
    private val advanceTimeSumMillis = HashMap<UUID, Long>()
    private val advanceCount = HashMap<UUID, Int>()
    private var startMillis = 0L
    private val bossBar = usePlayerDynamicTaskDisplay(styled(SCORE_GOAL, ChatFormatting.YELLOW)).also {
        it.setPercent(0f)
    }

    override val maxDuration: Duration
        get() = 2.minutes

    override fun prepare() {
        for (player in players()) {
            manager.domains[player.uuid]?.teleport(player)
        }
    }

    override fun go() {
        for (player in players()) {
            val domain = manager.domains[player.uuid] ?: continue
            domain.teleport(player)
            PlayerInventoryAccess.setSelectedSlot(player, 4)
            domain.setBlocks(sequence.items.first(), player)
        }

        PlayerInventoryHooks.SLOT_CHANGE.registerWith(hooks) { player, slot ->
            if (slot != 4) PlayerInventoryAccess.setSelectedSlot(player, 4)
        }

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks) { player, _, _ -> invokeRayCaster(player) }
        PlayerSwingHandHook.HOOK.registerWith(hooks) { player, _ -> invokeRayCaster(player) }

        startMillis = System.currentTimeMillis()
    }

    private fun invokeRayCaster(player: Player): InteractionResult {
        if (winManager.gameOver || player !is ServerPlayer) return InteractionResult.FAIL

        val domain = manager.domains[player.uuid] ?: return InteractionResult.FAIL

        val clicks = stats.increment(player, Clicks)

        if (domain.rayCaster(player, SPHERE_RADIUS)) {
            onHitCorrect(player, clicks, domain)

            return InteractionResult.FAIL
        }

        currentStreak[player.uuid] = 0
        stats.increment(player, Misses)
        updateAccuracy(player, data.getScore(player), clicks)

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.3f, 0.2f)
        return InteractionResult.PASS
    }

    private fun onHitCorrect(
        player: ServerPlayer,
        clicks: Int,
        domain: AimMasterDomain
    ) {
        data.addScore(player, 1)
        val newScore = data.getScore(player)
        bossBar.getBossBar(player).progress = newScore.toFloat() / SCORE_GOAL

        val streak = (currentStreak[player.uuid] ?: 0) + 1
        currentStreak[player.uuid] = streak
        stats.modify(player, Streak) { maxOf(it, streak) }
        updateAccuracy(player, newScore, clicks)
        recordAdvanceTime(player)

        val target = domain.currentTarget

        if (target != null) {
            level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                target.x.toDouble(),
                target.y.toDouble(),
                target.z.toDouble(),
                12,
                0.4,
                0.4,
                0.4,
                0.01
            )
        }
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.5f, 0.8f)

        if (newScore >= SCORE_GOAL) win(player)
        else manager.advancePlayer(player)
    }

    private fun updateAccuracy(player: ServerPlayer, hits: Int, clicks: Int) {
        val accuracy = if (clicks == 0) 0f else hits.toFloat() / clicks
        stats.set(player, Accuracy, accuracy)
    }

    private fun recordAdvanceTime(player: ServerPlayer) {
        val uuid = player.uuid
        val now = System.currentTimeMillis()
        val last = lastAdvanceMillis[uuid] ?: startMillis

        val sum = (advanceTimeSumMillis[uuid] ?: 0L) + (now - last)
        val count = (advanceCount[uuid] ?: 0) + 1
        advanceTimeSumMillis[uuid] = sum
        advanceCount[uuid] = count
        lastAdvanceMillis[uuid] = now

        val avgMillis = sum.toDouble() / count
        val seconds = round(avgMillis / 100.0).toFloat() / 10f

        stats.set(player, AvgAdvanceTime, seconds)
    }

    private fun win(winner: ServerPlayer) {
        val domain = manager.domains[winner.uuid] ?: return
        domain.removeBlocks(sequence.items.last())

        val task = WinAnimationTask(winner, domain, sequence)
        val taskHandle = gameHandle.rootScheduler.interval(task, 5)
        winManager.complete().then(taskHandle::cancel)
    }
}

private class WinAnimationTask(
    private val player: ServerPlayer,
    private val domain: AimMasterDomain,
    private val sequence: AimMasterSequence
) : SchedulerAction {
    var time = 0

    override fun run(info: RunningTask) {
        val i = time / 2
        val sequenceItem = sequence.items[sequence.items.size - 1 - i]

        if (time % 2 == 0) domain.setBlocks(sequenceItem, player)
        else domain.removeBlocks(sequenceItem)

        if (i >= sequence.items.size - 1) info.cancel()
        time++
    }
}

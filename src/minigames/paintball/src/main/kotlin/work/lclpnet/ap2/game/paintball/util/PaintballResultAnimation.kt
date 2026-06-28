package work.lclpnet.ap2.game.paintball.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.type.TeamRef
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamKey
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.title.TitleAnimation
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.title.Title
import work.lclpnet.kibu.translate.Translations
import java.util.*
import kotlin.math.min

private val DURATION_TICKS = Ticks.seconds(5)
private val FINAL_DELAY_TICKS = Ticks.seconds(1)

class PaintballResultAnimation(
    teams: Iterable<TeamRef>,
    data: IntScoreDataContainer<Team, TeamRef>,
    private val server: MinecraftServer,
    private val translations: Translations,
    private val callback: Runnable
) : TitleAnimation {

    private val entries: List<Entry>
    private val targetValues: DoubleArray
    private val maxPercent: Double
    private var time = 0

    init {
        entries = ArrayList<Entry>().also { list ->
            for (team in teams) {
                list.add(Entry(team.key, data.getScore(team)))
            }
        }

        val total = entries.sumOf { it.score }

        for (entry in entries) {
            entry.byTotal(total)
        }

        targetValues = entries.map { it.percent }.toDoubleArray()
        maxPercent = targetValues.maxOrNull() ?: 0.0
    }

    override fun tick(): Boolean {
        val t = time++

        if (t <= DURATION_TICKS) {
            updateTitle(t.toDouble())
            playAnimationSound(t.toDouble())

            if (t == DURATION_TICKS) {
                playFinalSound()
            }
        }

        return t >= DURATION_TICKS + FINAL_DELAY_TICKS
    }

    private fun playAnimationSound(t: Double) {
        val progress = (t / DURATION_TICKS).coerceIn(0.0, 1.0).toFloat()
        val minPitch = 0.5f
        val maxPitch = 1.6f
        val pitch = minPitch + progress * (maxPitch - minPitch)

        SoundHelper.playSound(server, SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.2f, pitch)
    }

    private fun playFinalSound() {
        SoundHelper.playSound(server, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 0.5f, 2f)
    }

    private fun updateTitle(t: Double) {
        val progress = (t / DURATION_TICKS).coerceIn(0.0, 1.0)
        val lerpedValue = maxPercent * progress

        val interpolated = targetValues.map { min(it, lerpedValue) * 100 }.toDoubleArray()

        for (player in PlayerLookup.all(server)) {
            val locale = translations.getLocale(player)
            val msg = createMessage(interpolated, locale)
            Title.get(player).title(Component.empty(), msg)
        }
    }

    private fun createMessage(interpolated: DoubleArray, locale: Locale): Component {
        val root = Component.empty()
        val totalChars = 35
        val occupied = interpolated.size * 5
        val spacerLen = if (interpolated.size > 1) (totalChars - occupied) / (interpolated.size - 1) else 0
        val spacer = " ".repeat(spacerLen)

        for ((i, entry) in entries.withIndex()) {
            if (i > 0) root.append(Component.literal(spacer))

            val str = String.format(locale, "%.1f%%", interpolated[i])
            root.append(Component.literal(str).withColor(entry.key.color))
        }

        return root
    }

    override fun begin() {
        for (player in players()) {
            Title.get(player).times(0, 20, 0)
        }
    }

    override fun destroy() {
        for (player in players()) {
            Title.get(player).resetTimes()
        }

        callback.run()
    }

    private fun players(): Iterable<ServerPlayer> = PlayerLookup.all(server)

    private class Entry(val key: TeamKey, val score: Int) {
        var percent = 0.0

        fun byTotal(total: Int) {
            percent = if (total == 0) 0.0 else score.toDouble() / total
        }
    }
}

package work.lclpnet.ap2.api.stats

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import org.slf4j.Logger
import work.lclpnet.ap2.ApConstants
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.network.CustomClickActionCallback
import work.lclpnet.kibu.translate.Translations
import java.util.*

class SessionStatsRecorder(val translations: Translations, val logger: Logger) {

    private val records = mutableMapOf<UUID, StatsResult>()
    private val statsDisplay = StatsDisplay(translations, logger)

    fun record(statsId: UUID, stats: StatsResult) {
        records[statsId] = stats
    }

    operator fun get(statsId: UUID) = records[statsId]

    fun init(hooks: HookRegistrar) {
        CustomClickActionCallback.HOOK.registerWith(hooks) { player, id, payload ->
            onCustomClickAction(player, id, payload)
        }
    }

    private fun onCustomClickAction(player: ServerPlayer, id: Identifier, payload: Optional<Tag>) {
        if (SHOW_SUMMARY != id) return

        val payload = payload.orElse(null) ?: return

        if (payload !is CompoundTag) return

        val idStr = payload.getString("id").orElse(null) ?: return

        val id: UUID?

        try {
            id = UUID.fromString(idStr)
        } catch (_: Throwable) {
            return
        }

        val stats = this[id]

        if (stats == null) {
            statsDisplay.unavailable(player)
            return
        }

        openSummary(player, stats)
    }

    fun openSummary(player: ServerPlayer, stats: StatsResult) {
        statsDisplay.openSummary(player, stats)
    }

    companion object {
        @JvmField val SHOW_SUMMARY = ApConstants.identifier("show_stats")
    }
}
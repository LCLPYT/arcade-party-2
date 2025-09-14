package work.lclpnet.ap2.api.stats

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import org.slf4j.Logger
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.core.hook.CustomClickActionCallback
import work.lclpnet.kibu.hook.HookRegistrar
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
        hooks.registerHook(CustomClickActionCallback.HOOK, CustomClickActionCallback { player, id, payload ->
            onCustomClickAction(player, id, payload)
        })
    }

    private fun onCustomClickAction(player: ServerPlayerEntity, id: Identifier, payload: Optional<NbtElement>) {
        if (SHOW_SUMMARY != id) return

        val payload = payload.orElse(null) ?: return

        if (payload !is NbtCompound) return

        val idStr = payload.getString("id", null) ?: return

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

        val detail = payload.getString("detail", null)

        if (detail == null) {
            openSummary(player, stats, id)
        } else {
            openDetail(player, stats, detail)
        }
    }

    fun openSummary(player: ServerPlayerEntity, stats: StatsResult, id: UUID) {
        statsDisplay.openSummary(player, stats, id)
    }

    fun openDetail(player: ServerPlayerEntity, stats: StatsResult, detail: String) {
        statsDisplay.openDetail(player, stats, detail)
    }

    companion object {
        @JvmField val SHOW_SUMMARY = ApConstants.identifier("show_stats")
    }
}
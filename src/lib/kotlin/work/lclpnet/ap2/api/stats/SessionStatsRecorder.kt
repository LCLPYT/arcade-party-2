package work.lclpnet.ap2.api.stats

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.core.hook.CustomClickActionCallback
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.translate.Translations
import java.util.*


class SessionStatsRecorder(val translations: Translations) {

    private val records = mutableMapOf<UUID, StatsResult<out SubjectRef>>()
    private val statsDisplay = StatsDisplay(translations)

    fun record(statsId: UUID, stats: StatsResult<out SubjectRef>) {
        records[statsId] = stats
    }

    operator fun get(statsId: UUID) = records[statsId]

    fun init(hooks: HookRegistrar) {
        hooks.registerHook(CustomClickActionCallback.HOOK, CustomClickActionCallback { player, id, payload ->
            onCustomClickAction(player, id, payload)
        })
    }

    private fun onCustomClickAction(player: ServerPlayerEntity, id: Identifier, payload: Optional<NbtElement>) {
        if (PACKET_ID != id) return

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
            translations.translateText("ap2.view_stats.unavailable").formatted(Formatting.RED).sendTo(player)
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.5f, 0.5f)
            return
        }

        openStats(player, stats)
    }

    fun openStats(player: ServerPlayerEntity, stats: StatsResult<out SubjectRef>) {
        statsDisplay.open(player, stats)
    }

    companion object {
        @JvmField
        val PACKET_ID = ApConstants.identifier("show_stats")
    }
}
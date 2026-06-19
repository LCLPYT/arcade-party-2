package work.lclpnet.ap2.game.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.ext.server
import work.lclpnet.ap2.ext.translations
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.kibu.title.Title
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

data class Announcer(
    private val translations: Translations,
    private val players: () -> Iterable<ServerPlayer>,
    private val sound: SoundEvent? = SoundEvents.NOTE_BLOCK_PLING.value(),
    private val category: SoundSource = SoundSource.RECORDS,
    private val volume: Float = 0.5f,
    private val pitch: Float = 0.5f,
    private val fadeInTicks: Int = 5,
    private val stayTicks: Int = 30,
    private val fadeOutTicks: Int = 5,
) {

    constructor(translations: Translations, server: MinecraftServer) : this(
        translations,
        { PlayerLookup.all(server) }
    )

    fun silent() = copy(sound = null)

    fun withSound(sound: SoundEvent, category: SoundSource, volume: Float, pitch: Float) = copy(
        sound = sound,
        category = category,
        volume = volume.coerceAtLeast(0f),
        pitch = pitch.coerceIn(0.5f..2f)
    )

    fun withTimes(fadeInTicks: Int, stayTicks: Int, fadeOutTicks: Int) = copy(
        fadeInTicks = fadeInTicks.coerceAtLeast(0),
        stayTicks = stayTicks.coerceAtLeast(0),
        fadeOutTicks = fadeOutTicks.coerceAtLeast(0),
    )

    fun announceSubtitle(translationKey: String) {
        announce(null, translationKey)
    }

    fun announce(titleKey: String?, subtitleKey: String?) {
        val title = if (titleKey != null) {
            translations.translateText(titleKey).withStyle(ChatFormatting.AQUA)
        } else null

        val subtitle = if (subtitleKey != null) {
            translations.translateText(subtitleKey).withStyle(ChatFormatting.DARK_GREEN)
        } else null

        announce(title, subtitle)
    }

    fun announce(title: TranslatedText?, subtitle: TranslatedText?) {
        for (player in players()) {
            val titleText = if (title != null) title.translateFor(player) else Component.empty()
            val subTitleText = if (subtitle != null) subtitle.translateFor(player) else Component.empty()

            Title.get(player).title(titleText, subTitleText, fadeInTicks, stayTicks, fadeOutTicks)

            sound?.let {
                player.playNotifySound(it, category, volume, pitch)
            }
        }
    }
}

fun MiniGameInstance.useAnnouncer() = Announcer(translations, server)
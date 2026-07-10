package work.lclpnet.ap2.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.util.PlayerUtil
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.notica.network.NoticaNetworking
import java.net.URI
import java.util.function.Predicate
import kotlin.time.Duration.Companion.seconds

class Hints(private val translations: Translations, private val server: MinecraftServer) {

    constructor(gameHandle: MiniGameHandle) : this(gameHandle.translations, gameHandle.server)

    fun sendModHint(mod: Mod) {
        for (player in PlayerLookup.all(server)) {
            if (mod.installed.test(player)) continue

            val modLabel = Component.literal(mod.name + " ↗")
                .withStyle { style ->
                    style
                        .withColor(0x145ee8)
                        .withBold(false)
                        .withUnderlined(true)
                        .withHoverEvent(
                            HoverEvent.ShowText(
                                translations.translateText(
                                    player,
                                    "ap2.hint.click_open",
                                    mod.link.toString()
                                )
                            )
                        )
                        .withClickEvent(ClickEvent.OpenUrl(mod.link))
                }

            val sub = translations.translateText(player, "ap2.hint.mod", modLabel)
                .withStyle(ChatFormatting.YELLOW)
                .withStyle { style -> style.withBold(false) }

            val hint = translations.translateText(player, "ap2.hint", sub)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)

            player.sendSystemMessage(hint)
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 0.5f, 0.5f)
        }
    }

    fun sendBeforeReady(gameHandle: MiniGameHandle, mod: Mod) {
        val initialDelay = PlayerUtil.getLoadingDelay(gameHandle.participants.count())

        val delay = (initialDelay - 3.seconds).coerceAtLeast(0.seconds)

        gameHandle.scheduler.timeout(delay.inWholeTicks) { ->
            sendModHint(mod)
        }
    }

    data class Mod(
        val name: String,
        val link: URI,
        val installed: Predicate<ServerPlayer>,
    ) {
        companion object {
            val Notica = Mod(
                "Notica",
                URI.create("https://modrinth.com/mod/notica")
            ) { player ->
                NoticaNetworking.getInstance().understandsProtocol(player)
            }
        }
    }
}
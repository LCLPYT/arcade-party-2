package work.lclpnet.ap2.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting.*
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ApConstants
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TextTranslatable

private const val PREPARATION = "ap2.status.preparation"
private const val BRANDING = "lclpnet.work"

class TablistManager(
    val translations: Translations,
    val server: MinecraftServer,
) {
    var status: TextTranslatable? = null

    fun update() {
        for (player in PlayerLookup.all(server)) {
            update(player)
        }
    }

    fun update(player: ServerPlayer) {
        val header = mutableListOf<Component>()
        val footer = mutableListOf<Component>()

        header.add(translations.translateText("game.ap2.title")
            .withStyle(GOLD, BOLD)
            .translateFor(player))

        header.add(Component.literal(ApConstants.TABLIST_SEPARATOR).withStyle(DARK_GREEN, BOLD, STRIKETHROUGH))

        val status = status

        if (status != null) {
            header.add(
                status.translateTo(translations.getLanguage(player))
                .copy()
                .withStyle(GREEN)
            )

            header.add(Component.literal(ApConstants.TABLIST_SEPARATOR_SM).withStyle(DARK_GREEN, STRIKETHROUGH))
        }

        footer.add(Component.literal(ApConstants.TABLIST_SEPARATOR).withStyle(DARK_GREEN, BOLD, STRIKETHROUGH))

        footer.add(translations.translateText(
            "ap2.playing_on",
            Component.literal(BRANDING).withStyle(YELLOW, BOLD)
        ).withStyle(AQUA).translateFor(player))

        val mergedHeader = header.reduceOrNull { x, y -> Component.empty().append(x).append("\n").append(y) }
        val mergedFooter = footer.reduceOrNull { x, y -> Component.empty().append(x).append("\n").append(y) }

        val packet = ClientboundTabListPacket(
            mergedHeader ?: Component.empty(),
            mergedFooter ?: Component.empty()
        )

        player.connection.send(packet)
    }

    fun setPreparation() {
        status = translations.translateText(PREPARATION)
    }
}
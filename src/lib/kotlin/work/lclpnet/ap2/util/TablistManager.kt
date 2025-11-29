package work.lclpnet.ap2.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Formatting.*
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

    fun update(player: ServerPlayerEntity) {
        val header = mutableListOf<Text>()
        val footer = mutableListOf<Text>()

        header.add(translations.translateText("game.ap2.title")
            .formatted(GOLD, BOLD)
            .translateFor(player))

        header.add(Text.literal(ApConstants.TABLIST_SEPARATOR).formatted(DARK_GREEN, BOLD, STRIKETHROUGH))

        val status = status

        if (status != null) {
            header.add(
                status.translateTo(translations.getLanguage(player))
                .copy()
                .formatted(GREEN)
            )

            header.add(Text.literal(ApConstants.TABLIST_SEPARATOR_SM).formatted(DARK_GREEN, STRIKETHROUGH))
        }

        footer.add(Text.literal(ApConstants.TABLIST_SEPARATOR).formatted(DARK_GREEN, BOLD, STRIKETHROUGH))

        footer.add(translations.translateText(
            "ap2.playing_on",
            Text.literal(BRANDING).formatted(YELLOW, BOLD)
        ).formatted(AQUA).translateFor(player))

        val mergedHeader = header.reduceOrNull { x, y -> Text.empty().append(x).append("\n").append(y) }
        val mergedFooter = footer.reduceOrNull { x, y -> Text.empty().append(x).append("\n").append(y) }

        val packet = PlayerListHeaderS2CPacket(
            mergedHeader ?: Text.empty(),
            mergedFooter ?: Text.empty()
        )

        player.networkHandler.sendPacket(packet)
    }

    fun setPreparation() {
        status = translations.translateText(PREPARATION)
    }
}
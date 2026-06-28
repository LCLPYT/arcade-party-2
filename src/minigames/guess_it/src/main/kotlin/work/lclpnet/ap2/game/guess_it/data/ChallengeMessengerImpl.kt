package work.lclpnet.ap2.game.guess_it.data

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*

class ChallengeMessengerImpl(
    private val world: ServerLevel,
    private val translations: Translations,
    private val answerId: Identifier
) : ChallengeMessenger {
    private var task: TranslatedText? = null
    private var options: Array<Component>? = null

    override fun task(task: TranslatedText) {
        this.task = task
    }

    override fun options(vararg options: Component) {
        this.options = options.toList().toTypedArray()
    }

    fun send() {
        if (task == null) return

        val msg = task!!.withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)

        for (player in PlayerLookup.level(world)) {
            repeat(20) {
                player.sendSystemMessage(Component.empty())
            }

            player.sendSystemMessage(msg.translateFor(player))
        }

        if (options != null) {
            sendOptions(options!!)
        }
    }

    fun reset() {
        task = null
        options = null
    }

    private fun sendOptions(options: Array<Component>) {
        val players = PlayerLookup.level(world)
        var letter = 'A'

        for (option in options) {
            val payload = StringTag.valueOf(letter.toString())
            val clickEvent = ClickEvent.Custom(answerId, Optional.of(payload))

            for (player in players) {
                val hoverMsg = translations.translateText(
                    player,
                    "hover_option",
                    FormatWrapper.styled(letter, ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN)

                val hoverEvent = HoverEvent.ShowText(hoverMsg)

                val msg = Component.literal("$letter) ").withStyle(ChatFormatting.YELLOW)
                    .append(option.copy().withStyle(ChatFormatting.AQUA))
                    .withStyle {
                        it.withClickEvent(clickEvent).withHoverEvent(hoverEvent)
                    }

                player.sendSystemMessage(msg)
            }

            letter++
        }
    }
}

package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.ChatFormatting
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.ServerMessageHooks
import work.lclpnet.kibu.hook.network.CustomClickActionCallback
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*

class InputManager(
    private val choices: PlayerChoices,
    private val translations: Translations,
    private val participants: Participants,
    private val messenger: ChallengeMessenger,
    private val answerId: Identifier
) : InputInterface {
    private var inputValue: InputValue? = null
    private var optionValue: OptionValue? = null
    private var locked = false

    fun init(hooks: HookRegistrar) {
        ServerMessageHooks.ALLOW_CHAT_MESSAGE.registerWith(hooks) { message, sender, _ ->
            onChat(message, sender)
            false
        }

        CustomClickActionCallback.HOOK.registerWith(hooks) { player, id, payload ->
            if (id != answerId) return@registerWith

            val payload = payload.orElse(null)

            if (payload is StringTag) {
                input(player, payload.value)
            }
        }
    }

    private fun onChat(signedMessage: PlayerChatMessage, player: ServerPlayer) {
        val input = signedMessage.signedBody().content()

        input(player, input)
    }

    fun input(player: ServerPlayer, input: String) {
        if (!participants.isParticipating(player) || locked) return

        val res: Pair<String?, TranslatedText?>

        val inputValue = this.inputValue

        if (inputValue != null) {
            if (inputValue.once && hasAnswered(player)) {
                val msg = translations.translateText(player, "already_answered").withStyle(ChatFormatting.RED)
                player.sendSystemMessage(msg)
                player.playNotifySound(SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 0.5f, 0f)
                return
            }

            res = inputValue.validate(input, player)
        } else if (optionValue != null) {
            res = optionValue!!.validate(input)
        } else {
            return
        }

        val (transformedInput, err) = res

        if (err != null) {
            player.sendSystemMessage(err.translateFor(player))
            return
        }

        if (transformedInput != null) {
            onAnswer(player, transformedInput)
        }
    }

    private fun onAnswer(player: ServerPlayer, input: String) {
        choices.set(player, input)

        val msg = translations.translateText(
            player,
            "guessed",
            FormatWrapper.styled(input, ChatFormatting.YELLOW)
        ).withStyle(ChatFormatting.GREEN)

        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.75f, 1.5f)

        player.sendSystemMessage(msg)
    }

    private fun hasAnswered(player: ServerPlayer): Boolean =
        choices.getInt(player) != null

    override fun expectInput(): InputValue {
        reset()

        val inputValue = InputValue()

        this.inputValue = inputValue

        return inputValue
    }

    override fun expectSelection(vararg options: Component) {
        reset()

        messenger.options(*options)

        optionValue = OptionValue(translations, options.size)
    }

    fun setLocked(locked: Boolean) {
        this.locked = locked
    }

    fun reset() {
        inputValue = null
        optionValue = null
        choices.clear()
        locked = false
    }
}


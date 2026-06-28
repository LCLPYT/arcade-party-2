package work.lclpnet.ap2.game.guess_it.challenge

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting.BOLD
import net.minecraft.ChatFormatting.DARK_GREEN
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.OptionMaker
import work.lclpnet.ap2.impl.util.TextUtil
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.title.Title
import java.util.*
import kotlin.math.pow

private val DURATION_TICKS = Ticks.seconds(11)
private const val SOUND_DELAY_TICKS = 30
private val REPEAT_DELAY_TICKS = Ticks.seconds(4)

class SoundChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val soundSubtitles: SoundSubtitles
) : Challenge {

    private var correct: SoundEvent? = null
    private var pitch = 1f
    private var correctOption = -1

    override fun id() = "sound"

    override val preparationKey = PREPARE_LISTEN

    override val durationTicks = DURATION_TICKS + SOUND_DELAY_TICKS + REPEAT_DELAY_TICKS

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("sound.guess"))

        val soundEvents = soundSubtitles.soundEvents
        val soundOptions = OptionMaker.createOptions(soundEvents, 4, random)

        correctOption = random.nextInt(soundOptions.size)
        correct = soundOptions[correctOption]
        randomizePitch()

        val options: List<Component> = soundOptions.map { TextUtil.getVanillaName(it) }

        input.expectSelection(*options.toTypedArray())

        playFirst()
    }

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        stopSound()

        result.correctAnswer = TextUtil.getVanillaName(correct!!)
        result.grantIfCorrect(gameHandle.participants, correctOption, choices::getOption)
    }

    private fun playFirst() {
        playSound()

        gameHandle.scheduler.timeout(REPEAT_DELAY_TICKS, ::prepareSecond)
    }

    private fun prepareSecond() {
        stopSound()

        val msg = gameHandle.translations.translateText("again")
            .withStyle(DARK_GREEN, BOLD)

        for (player in PlayerLookup.level(world)) {
            Title.get(player).title(Component.empty(), msg.translateFor(player))
        }

        gameHandle.scheduler.timeout(SOUND_DELAY_TICKS, ::playSound)
    }

    private fun randomizePitch() {
        val keyOffset = Mth.clamp(12f, 0f, 24f)
        val key = random.nextFloat(keyOffset)

        pitch = 2.0.pow(((key - keyOffset * 0.5) / keyOffset)).toFloat()
        pitch = Mth.clamp(pitch, 0.5f, 2f)
    }

    private fun playSound() {
        for (player in PlayerLookup.level(world)) {
            ServerPlayerAccess.playSoundToPlayer(player, correct!!, SoundSource.MASTER, 1f, pitch)
        }
    }

    private fun stopSound() {
        val packet = ClientboundStopSoundPacket(correct!!.location(), SoundSource.MASTER)

        for (player in PlayerLookup.level(world)) {
            player.connection.send(packet)
        }
    }

    override fun shouldPlayBeginSound(): Boolean = false
}

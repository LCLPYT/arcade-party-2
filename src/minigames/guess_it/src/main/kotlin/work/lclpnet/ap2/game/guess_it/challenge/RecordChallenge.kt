package work.lclpnet.ap2.game.guess_it.challenge

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.guess_it.data.*
import work.lclpnet.ap2.game.guess_it.util.GuessItDisplay
import work.lclpnet.ap2.game.guess_it.util.OptionMaker
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*

class RecordChallenge(
    private val gameHandle: MiniGameHandle,
    private val world: ServerLevel,
    private val random: Random,
    private val display: GuessItDisplay
) : Challenge {

    private var correct: Item? = null
    private var correctOption = -1

    override fun id() = "record"

    override val preparationKey = PREPARE_GUESS

    override val durationTicks = Ticks.seconds(15)

    override fun begin(input: InputInterface, messenger: ChallengeMessenger) {
        val translations = gameHandle.translations
        messenger.task(translations.translateText("music_disc"))

        val discs = getMusicDiscs()
        val opts = OptionMaker.createOptions(discs, 4, random)

        correctOption = random.nextInt(opts.size)
        correct = opts[correctOption]

        display.displayItem(ItemStack(correct!!))

        ItemHelper.getJukeboxSong(correct).ifPresent { song ->
            val sound = song.soundEvent().value()

            for (player in PlayerLookup.level(world)) {
                ServerPlayerAccess.playSoundToPlayer(player, sound, SoundSource.RECORDS, 0.5f, 1f)
            }
        }

        val descriptions: List<Component> = opts.mapNotNull { item ->
            ItemHelper.getJukeboxSong(item).map { it.description() }.orElse(null)
        }

        input.expectSelection(*descriptions.toTypedArray())
    }

    private fun getMusicDiscs(): List<Item> =
        BuiltInRegistries.ITEM.listElements()
            .sorted(Comparator.comparing { reference -> reference.key().identifier() })
            .map { it.value() }
            .filter { it.components().has(DataComponents.JUKEBOX_PLAYABLE) }
            .toList()

    override fun evaluate(choices: PlayerChoices, result: ChallengeResult) {
        val answer = ItemHelper.getJukeboxSong(correct)
            .map { it.description() }
            .orElse(null)

        result.correctAnswer = answer
        result.grantIfCorrect(gameHandle.participants, correctOption, choices::getOption)
    }

    override fun destroy() {
        ItemHelper.getJukeboxSong(correct).ifPresent { song ->
            val sound = song.soundEvent().value()
            val packet = ClientboundStopSoundPacket(sound.location(), SoundSource.RECORDS)

            for (player in PlayerLookup.level(world)) {
                player.connection.send(packet)
            }
        }
    }
}

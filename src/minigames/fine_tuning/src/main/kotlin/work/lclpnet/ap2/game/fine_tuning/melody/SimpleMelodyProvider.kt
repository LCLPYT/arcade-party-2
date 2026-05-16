package work.lclpnet.ap2.game.fine_tuning.melody

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument.*
import kotlin.random.Random

private val INSTRUMENTS = listOf(
    HARP,
    GUITAR,
    FLUTE,
    BELL,
    CHIME,
    XYLOPHONE,
    COW_BELL,
    IRON_XYLOPHONE,
    COW_BELL,
    DIDGERIDOO,
    BIT,
    BANJO,
    PLING,
    TRUMPET,
    TRUMPET_EXPOSED,
    TRUMPET_OXIDIZED,
    TRUMPET_WEATHERED
)

class SimpleMelodyProvider(
    private val random: Random,
    private val notesProvider: NotesProvider,
    private val noteCount: Int
) : MelodyProvider {

    override fun nextMelody(): Melody {
        val instrument = INSTRUMENTS.random(random)
        val notes = Array(noteCount) { Note.FIS3 }
        notesProvider.randomizeNotes(notes)
        return Melody(instrument, notes)
    }
}

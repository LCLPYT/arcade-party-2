package work.lclpnet.ap2.game.fine_tuning.melody;

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import java.util.Arrays;
import java.util.Random;

import static net.minecraft.world.level.block.state.properties.NoteBlockInstrument.*;

public class SimpleMelodyProvider implements MelodyProvider {

    private final NoteBlockInstrument[] instruments = new NoteBlockInstrument[] {
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
    };
    private final Random random;
    private final NotesProvider notesProvider;
    private final int noteCount;

    public SimpleMelodyProvider(Random random, NotesProvider notesProvider, int noteCount) {
        this.random = random;
        this.notesProvider = notesProvider;
        this.noteCount = noteCount;
    }

    @Override
    public Melody nextMelody() {
        NoteBlockInstrument instrument = instruments[random.nextInt(instruments.length)];

        Note[] notes = new Note[noteCount];
        Arrays.fill(notes, Note.FIS3);

        notesProvider.randomizeNotes(notes);

        return new Melody(instrument, notes);
    }
}

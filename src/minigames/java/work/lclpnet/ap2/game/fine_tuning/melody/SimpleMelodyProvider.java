package work.lclpnet.ap2.game.fine_tuning.melody;

import net.minecraft.block.enums.NoteBlockInstrument;

import java.util.Arrays;
import java.util.Random;

public class SimpleMelodyProvider implements MelodyProvider {

    private final NoteBlockInstrument[] instruments = new NoteBlockInstrument[] {
            NoteBlockInstrument.HARP, NoteBlockInstrument.GUITAR, NoteBlockInstrument.FLUTE, NoteBlockInstrument.BELL, NoteBlockInstrument.CHIME,
            NoteBlockInstrument.XYLOPHONE, NoteBlockInstrument.COW_BELL, NoteBlockInstrument.IRON_XYLOPHONE, NoteBlockInstrument.COW_BELL,
            NoteBlockInstrument.DIDGERIDOO, NoteBlockInstrument.BIT, NoteBlockInstrument.BANJO, NoteBlockInstrument.PLING
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

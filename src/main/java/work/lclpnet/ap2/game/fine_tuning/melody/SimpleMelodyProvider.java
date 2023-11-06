package work.lclpnet.ap2.game.fine_tuning.melody;

import net.minecraft.block.enums.Instrument;

import java.util.Arrays;
import java.util.Random;

public class SimpleMelodyProvider implements MelodyProvider {

    private final Instrument[] instruments = new Instrument[] {
            Instrument.HARP, Instrument.BASS, Instrument.GUITAR, Instrument.FLUTE, Instrument.BELL, Instrument.CHIME,
            Instrument.XYLOPHONE, Instrument.COW_BELL, Instrument.IRON_XYLOPHONE, Instrument.COW_BELL,
            Instrument.DIDGERIDOO, Instrument.BIT, Instrument.BANJO, Instrument.PLING
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
        Instrument instrument = instruments[random.nextInt(instruments.length)];

        Note[] notes = new Note[noteCount];
        Arrays.fill(notes, Note.FIS3);

        notesProvider.randomizeNotes(notes);

        return new Melody(instrument, notes);
    }
}

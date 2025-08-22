package work.lclpnet.ap2.game.fine_tuning.melody;

import java.util.Random;

import static work.lclpnet.ap2.game.fine_tuning.melody.Note.F4;
import static work.lclpnet.ap2.game.fine_tuning.melody.Note.values;

public class SimpleNotesProvider implements NotesProvider {

    private final Random random;

    public SimpleNotesProvider(Random random) {
        this.random = random;
    }

    @Override
    public void randomizeNotes(Note[] notes) {
        // randomize base tone from FIS3 to F4
        var allNotes = values();
        Note base = allNotes[random.nextInt(F4.ordinal() + 1)];

        // randomize major / minor
        ScaleType type = randomElement(ScaleType.values());

        // randomize tones from scale
        var scaleNotes = Scale.getScaleNotes(base, type);

        // last tone should be the base tone or two tones above

        notes[0] = base;
        notes[1] = randomElement(scaleNotes);
        notes[2] = randomElement(scaleNotes);
        notes[3] = randomElement(scaleNotes);

        if (random.nextBoolean()) {
            notes[4] = base;
        } else {
            // use major / minor third
            notes[4] = base.transpose(type.getThirdSteps());
        }
    }

    private <T> T randomElement(T[] array) {
        return array[random.nextInt(array.length)];
    }
}

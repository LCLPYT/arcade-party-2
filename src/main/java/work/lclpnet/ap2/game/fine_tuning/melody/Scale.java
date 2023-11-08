package work.lclpnet.ap2.game.fine_tuning.melody;

public class Scale {

    public static Note[] getScaleNotes(Note base, ScaleType type) {
        return switch (type) {
            case MAJOR -> getMajorNotes(base);
            case MINOR -> getMinorNotes(base);
        };
    }

    public static Note[] getMajorNotes(Note base) {
        int idx = base.ordinal();

        if (idx > Note.G4.ordinal()) {
            throw new IllegalArgumentException("Base note is too high");
        }

        Note[] notes = new Note[8];

        notes[0] = base;
        notes[1] = notes[0].transpose(2);
        notes[2] = notes[1].transpose(2);
        notes[3] = notes[2].transpose(1);
        notes[4] = notes[3].transpose(2);
        notes[5] = notes[4].transpose(2);
        notes[6] = notes[5].transpose(2);
        notes[7] = notes[6].transpose(1);

        return notes;
    }

    public static Note[] getMinorNotes(Note base) {
        if (base.ordinal() > Note.FIS4.ordinal()) {
            throw new IllegalArgumentException("Base note is too high");
        }

        Note[] notes = new Note[8];

        notes[0] = base;
        notes[1] = notes[0].transpose(2);
        notes[2] = notes[1].transpose(1);
        notes[3] = notes[2].transpose(2);
        notes[4] = notes[3].transpose(2);
        notes[5] = notes[4].transpose(1);
        notes[6] = notes[5].transpose(2);
        notes[7] = notes[6].transpose(1);

        return notes;
    }

    private Scale() {}
}

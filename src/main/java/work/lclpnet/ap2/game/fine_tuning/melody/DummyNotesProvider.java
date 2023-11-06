package work.lclpnet.ap2.game.fine_tuning.melody;

import static work.lclpnet.ap2.game.fine_tuning.melody.Note.*;

public class DummyNotesProvider implements NotesProvider {

    @Override
    public void randomizeNotes(Note[] notes) {
        notes[0] = FIS3;
        notes[1] = GIS3;
        notes[2] = B3;
        notes[3] = D4;
        notes[4] = E4;
    }
}

package work.lclpnet.ap2.game.fine_tuning.melody;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static work.lclpnet.ap2.game.fine_tuning.melody.Note.*;

class ScaleTest {

    @Test
    void cMajor() {
        var notes = Scale.getMajorNotes(C4);

        assertArrayEquals(new Note[] {C4, D4, E4, F4, G4, A4, B4, C5}, notes);
    }
}
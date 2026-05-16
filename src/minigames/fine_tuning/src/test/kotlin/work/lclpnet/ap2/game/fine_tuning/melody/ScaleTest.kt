package work.lclpnet.ap2.game.fine_tuning.melody

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScaleTest {

    @Test
    fun cMajor() {
        val notes = Scale.getMajorNotes(Note.C4)
        assertEquals(listOf(Note.C4, Note.D4, Note.E4, Note.F4, Note.G4, Note.A4, Note.B4, Note.C5), notes)
    }
}

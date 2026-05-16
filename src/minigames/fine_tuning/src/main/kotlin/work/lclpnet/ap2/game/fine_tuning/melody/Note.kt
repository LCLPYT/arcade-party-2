package work.lclpnet.ap2.game.fine_tuning.melody

enum class Note(private val distance: Int) {
    // octave 3
    FIS3(1),
    G3(1),
    GIS3(1),
    A3(1),
    AIS3(1),
    B3(2),
    // octave 4
    C4(1),
    CIS4(1),
    D4(1),
    DIS4(1),
    E4(2),
    F4(1),
    FIS4(1),
    G4(1),
    GIS4(1),
    A4(1),
    AIS4(1),
    B4(2),
    // octave 5
    C5(1),
    CIS5(1),
    D5(1),
    DIS5(1),
    E5(2),
    F5(1),
    FIS5(1);

    fun transpose(halfSteps: Int): Note {
        val notes = entries
        var dist = distance

        for (i in ordinal + 1 until notes.size) {
            val note = notes[i]
            if (dist >= halfSteps) return note
            dist += note.distance
        }

        throw IllegalStateException("Note out of range")
    }
}

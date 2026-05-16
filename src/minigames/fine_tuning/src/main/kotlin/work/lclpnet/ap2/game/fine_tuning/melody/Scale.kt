package work.lclpnet.ap2.game.fine_tuning.melody

object Scale {

    fun getScaleNotes(base: Note, type: ScaleType): List<Note> = when (type) {
        ScaleType.MAJOR -> getMajorNotes(base)
        ScaleType.MINOR -> getMinorNotes(base)
    }

    fun getMajorNotes(base: Note): List<Note> {
        if (base.ordinal > Note.G4.ordinal) throw IllegalArgumentException("Base note is too high")

        return ArrayList<Note>(8).apply {
            add(0, base)
            add(1, this[0].transpose(2))
            add(2, this[1].transpose(2))
            add(3, this[2].transpose(1))
            add(4, this[3].transpose(2))
            add(5, this[4].transpose(2))
            add(6, this[5].transpose(2))
            add(7, this[6].transpose(1))
        }
    }

    fun getMinorNotes(base: Note): List<Note> {
        if (base.ordinal > Note.FIS4.ordinal) throw IllegalArgumentException("Base note is too high")

        return ArrayList<Note>(8).apply {
            add(0, base)
            add(1, this[0].transpose(2))
            add(2, this[1].transpose(1))
            add(3, this[2].transpose(2))
            add(4, this[3].transpose(2))
            add(5, this[4].transpose(1))
            add(6, this[5].transpose(2))
            add(7, this[6].transpose(1))
        }
    }
}

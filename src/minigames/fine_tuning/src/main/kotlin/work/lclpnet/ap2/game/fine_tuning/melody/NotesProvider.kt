package work.lclpnet.ap2.game.fine_tuning.melody

fun interface NotesProvider {
    fun randomizeNotes(notes: Array<Note>)
}

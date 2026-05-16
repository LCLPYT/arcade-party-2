package work.lclpnet.ap2.game.fine_tuning.melody

import kotlin.random.Random

class SimpleNotesProvider(private val random: Random) : NotesProvider {

    override fun randomizeNotes(notes: Array<Note>) {
        val allNotes = Note.entries.toTypedArray()
        val base = allNotes[random.nextInt(Note.F4.ordinal + 1)]
        val type = ScaleType.entries[random.nextInt(ScaleType.entries.size)]
        val scaleNotes = Scale.getScaleNotes(base, type)

        notes[0] = base
        notes[1] = scaleNotes.random(random)
        notes[2] = scaleNotes.random(random)
        notes[3] = scaleNotes.random(random)
        notes[4] = if (random.nextBoolean()) base else base.transpose(type.thirdSteps)
    }
}

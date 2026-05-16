package work.lclpnet.ap2.game.fine_tuning.melody

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument

data class Melody(val instrument: NoteBlockInstrument, val notes: Array<Note>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Melody) return false
        return instrument == other.instrument && notes.contentEquals(other.notes)
    }

    override fun hashCode(): Int = 31 * instrument.hashCode() + notes.contentHashCode()
}

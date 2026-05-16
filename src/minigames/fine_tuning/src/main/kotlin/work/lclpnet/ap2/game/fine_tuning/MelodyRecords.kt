package work.lclpnet.ap2.game.fine_tuning

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.fine_tuning.melody.Melody
import work.lclpnet.ap2.impl.game.data.type.PlayerRef

class MelodyRecords {

    private val entries = mutableListOf<Entry>()
    private val melodies = arrayOfNulls<Melody>(MELODY_COUNT)
    private var melodyNumber = 0

    fun record(reference: Melody, best: ServerPlayer, bestMelody: Melody, worst: ServerPlayer, worstMelody: Melody) {
        entries.add(Entry(
            MelodyEntry.create(best, bestMelody, reference),
            MelodyEntry.create(worst, worstMelody, reference)
        ))
    }

    fun recordMelody(melody: Melody) {
        melodies[melodyNumber++] = melody
    }

    fun getMelody(melodyNumber: Int): Melody = melodies[melodyNumber]!!

    fun getBestMelody(melodyNumber: Int): MelodyEntry = entries[melodyNumber].best

    fun getWorstMelody(melodyNumber: Int): MelodyEntry = entries[melodyNumber].worst

    data class Entry(val best: MelodyEntry, val worst: MelodyEntry)

    data class MelodyEntry(val playerRef: PlayerRef, val melody: Melody, val offsets: IntArray) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MelodyEntry) return false
            return playerRef == other.playerRef && melody == other.melody && offsets.contentEquals(other.offsets)
        }

        override fun hashCode(): Int {
            var result = playerRef.hashCode()
            result = 31 * result + melody.hashCode()
            result = 31 * result + offsets.contentHashCode()
            return result
        }

        companion object {
            fun create(player: ServerPlayer, melody: Melody, reference: Melody): MelodyEntry {
                val offsets = IntArray(reference.notes.size) { i ->
                    melody.notes[i].ordinal - reference.notes[i].ordinal
                }
                return MelodyEntry(PlayerRef.create(player), melody, offsets)
            }
        }
    }
}

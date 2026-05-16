package work.lclpnet.ap2.game.fine_tuning.melody

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import work.lclpnet.ap2.impl.util.SoundHelper

class FakeNoteBlockPlayer(
    private val noteBlocks: Array<BlockPos>,
    private val notes: IntArray,
    private val instruments: Array<NoteBlockInstrument>
) {

    fun play(player: ServerPlayer, index: Int) {
        val pos = noteBlocks[index]
        playAt(player, index, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
    }

    fun playAtPlayerPos(player: ServerPlayer, index: Int) {
        playAt(player, index, player.x, player.y, player.z)
    }

    private fun playAt(player: ServerPlayer, index: Int, x: Double, y: Double, z: Double) {
        val note = notes[index]
        val pos = noteBlocks[index]
        val instrument = instruments[index]
        val pitch: Float

        if (instrument.isTunable) {
            pitch = NoteBlock.getPitchFromNote(note)

            player.level().sendParticles(
                player,
                ParticleTypes.NOTE,
                false,
                false,
                pos.x + 0.5,
                pos.y + 1.2,
                pos.z + 0.5,
                0,
                note / 24.0,
                0.0,
                0.0,
                1.0
            )
        } else {
            pitch = 1.0f
        }

        SoundHelper.playSound(
            player,
            instrument.soundEvent.value(),
            SoundSource.RECORDS,
            x,
            y,
            z,
            3f,
            pitch
        )
    }

    fun getNoteBlock(index: Int): BlockPos = noteBlocks[index]

    fun setMelody(melody: Melody) {
        val melodyNotes = melody.notes
        val instrument = melody.instrument

        for (i in notes.indices) {
            notes[i] = if (i < melodyNotes.size) melodyNotes[i].ordinal else 0
            instruments[i] = instrument
        }
    }
}

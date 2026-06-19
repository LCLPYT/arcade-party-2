package work.lclpnet.ap2.game.fine_tuning

import com.mojang.math.Transformation
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import org.joml.Matrix4f
import work.lclpnet.ap2.game.fine_tuning.melody.FakeNoteBlockPlayer
import work.lclpnet.ap2.game.fine_tuning.melody.Melody
import work.lclpnet.ap2.game.fine_tuning.melody.Note
import work.lclpnet.ap2.impl.util.ColorUtil
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.dynamic_entities.PlayerSpecificDynamicEntity
import kotlin.math.abs
import kotlin.math.max

class FineTuningRoom(val pos: BlockPos, private val spawn: BlockPos, private val yaw: Float) {

    private val displays = ArrayList<PlayerSpecificDynamicEntity<Display.BlockDisplay>?>(5)
    private lateinit var noteBlocks: Array<BlockPos>
    private lateinit var notes: IntArray
    private lateinit var tmpNotes: IntArray
    private lateinit var instruments: Array<NoteBlockInstrument>
    private lateinit var nbPlayer: FakeNoteBlockPlayer
    private var temporary = false
    var testSignPos: BlockPos? = null

    fun setNoteBlocks(relNoteBlocks: Array<out Vec3i>) {
        noteBlocks = Array(relNoteBlocks.size) { j -> pos.offset(relNoteBlocks[j]) }
        notes = IntArray(noteBlocks.size)
        tmpNotes = IntArray(noteBlocks.size)
        instruments = Array(noteBlocks.size) { NoteBlockInstrument.HARP }
        nbPlayer = FakeNoteBlockPlayer(noteBlocks, notes, instruments)

        displays.clear()
        repeat(notes.size) { displays.add(null) }
    }

    fun teleport(player: ServerPlayer, world: ServerLevel) {
        player.teleportTo(world, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, emptySet(), yaw, 0f, true)
    }

    fun useNoteBlock(player: ServerPlayer, pos: BlockPos, manager: DynamicEntityManager): Boolean {
        val index = getNoteBlock(pos)
        if (index == -1) return false

        val transpose = if (player.isShiftKeyDown) -1 else 1
        setNote(index, notes[index] + transpose)
        playNote(player, index)
        removeDisplay(index, manager)
        return true
    }

    fun playNoteBlock(player: ServerPlayer, pos: BlockPos): Boolean {
        val index = getNoteBlock(pos)
        if (index == -1) return false
        playNote(player, index)
        return true
    }

    fun playNote(player: ServerPlayer, index: Int) {
        nbPlayer.play(player, index)
    }

    fun setNote(i: Int, note: Int) {
        notes[i] = Math.floorMod(note, 25)
    }

    fun getNoteBlock(pos: BlockPos): Int {
        for (i in noteBlocks.indices) {
            if (noteBlocks[i] == pos) return i
        }
        return -1
    }

    fun setTemporaryMelody(melody: Melody) {
        System.arraycopy(notes, 0, tmpNotes, 0, notes.size)
        temporary = true
        setMelody(melody)
    }

    fun restoreMelody() {
        if (!temporary) return
        System.arraycopy(tmpNotes, 0, notes, 0, tmpNotes.size)
        temporary = false
    }

    fun setMelody(melody: Melody) {
        nbPlayer.setMelody(melody)
    }

    fun getCurrentMelody(): Melody {
        restoreMelody()
        val instrument = instruments[0]
        val allNotes = Note.entries.toTypedArray()
        val currentNotes = Array(notes.size) { i -> allNotes[notes[i]] }
        return Melody(instrument, currentNotes)
    }

    fun calculateScore(baseMelody: Melody, reference: Melody): Int {
        restoreMelody()
        val refNotes = reference.notes
        var score = 0

        for (i in notes.indices) {
            val actual = notes[i]
            val expected = refNotes[i].ordinal
            val base = baseMelody.notes[i].ordinal
            val offset = abs(expected - base)
            val diff = abs(expected - actual)
            score += max(0, offset - diff)
        }

        return score
    }

    fun correctNoteCount(reference: Melody): Int {
        restoreMelody()
        var correct = 0

        for (i in notes.indices) {
            val actual = notes[i]
            val expected = reference.notes[i].ordinal

            if (actual == expected) {
                correct++
            }
        }

        return correct
    }

    fun isComplete(reference: Melody): Boolean {
        restoreMelody()
        val refNotes = reference.notes

        for (i in notes.indices) {
            if (notes[i] != refNotes[i].ordinal) return false
        }

        return true
    }

    fun markErrors(baseMelody: Melody, reference: Melody, manager: DynamicEntityManager, player: ServerPlayer) {
        removeDisplays(manager)
        restoreMelody()

        val refNotes = reference.notes

        for (i in notes.indices) {
            val actual = notes[i]
            val expected = refNotes[i].ordinal
            val base = baseMelody.notes[i].ordinal
            val offset = abs(expected - base)
            val diff = abs(expected - actual)

            if (diff == 0 || offset == 0) continue

            val error = diff.toFloat() / offset
            addDisplay(i, error, manager, player)
        }
    }

    fun addDisplay(note: Int, error: Float, manager: DynamicEntityManager, viewer: ServerPlayer) {
        if (note < 0 || note >= displays.size) return

        val pos = noteBlocks[note]
        val margin = 0.015f

        val display = Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, viewer.level())
        display.setPosRaw(pos.x + margin.toDouble(), pos.y + margin.toDouble(), pos.z + margin.toDouble())
        display.blockState = Blocks.NOTE_BLOCK.defaultBlockState()
        display.setTransformation(Transformation(Matrix4f().scale(1 - margin * 2)))
        display.setGlowingTag(true)

        val color = ColorUtil.lerpRgb(0xefe409, 0x890404, error)
        display.glowColorOverride = color

        val dynamic = PlayerSpecificDynamicEntity(display, viewer.uuid)
        displays[note] = dynamic
        manager.add(dynamic)
    }

    fun removeDisplay(note: Int, manager: DynamicEntityManager) {
        if (note < 0 || note >= displays.size) return
        val display = displays[note] ?: return
        displays[note] = null
        manager.remove(display)
    }

    fun removeDisplays(manager: DynamicEntityManager) {
        for (i in displays.indices) {
            val display = displays[i] ?: continue
            manager.remove(display)
            displays[i] = null
        }
    }
}

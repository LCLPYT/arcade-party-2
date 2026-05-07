package work.lclpnet.ap2.game.fine_tuning;

import com.mojang.math.Transformation;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import work.lclpnet.ap2.game.fine_tuning.melody.FakeNoteBlockPlayer;
import work.lclpnet.ap2.game.fine_tuning.melody.Melody;
import work.lclpnet.ap2.game.fine_tuning.melody.Note;
import work.lclpnet.ap2.impl.util.ColorUtil;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;
import work.lclpnet.gaco.dynamic_entities.PlayerSpecificDynamicEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.lang.Math.abs;
import static java.lang.Math.max;

class FineTuningRoom {

    @Getter
    private final BlockPos pos;
    private final BlockPos spawn;
    private final float yaw;
    private final List<@Nullable PlayerSpecificDynamicEntity<Display.BlockDisplay>> displays = new ArrayList<>(5);
    private BlockPos[] noteBlocks = null;
    private int[] notes = null, tmpNotes = null;
    private NoteBlockInstrument[] instruments = null;
    private FakeNoteBlockPlayer nbPlayer = null;
    private boolean temporary = false;
    @Setter @Getter
    private @Nullable BlockPos testSignPos = null;

    public FineTuningRoom(BlockPos pos, BlockPos spawn, float yaw) {
        this.pos = pos;
        this.spawn = spawn;
        this.yaw = yaw;
    }

    public void setNoteBlocks(Vec3i[] relNoteBlock) {
        BlockPos[] noteBlocks = new BlockPos[relNoteBlock.length];

        for (int j = 0; j < relNoteBlock.length; j++) {
            noteBlocks[j] = pos.offset(relNoteBlock[j]);
        }

        this.noteBlocks = noteBlocks;

        this.notes = new int[noteBlocks.length];
        Arrays.fill(notes, 0);

        this.tmpNotes = new int[noteBlocks.length];
        Arrays.fill(tmpNotes, 0);

        instruments = new NoteBlockInstrument[noteBlocks.length];
        Arrays.fill(instruments, NoteBlockInstrument.HARP);

        this.nbPlayer = new FakeNoteBlockPlayer(noteBlocks, notes, instruments);

        displays.clear();

        for (int i = 0; i < notes.length; i++) {
            displays.add(null);
        }
    }

    public void teleport(ServerPlayer player, ServerLevel world) {
        double x = spawn.getX() + 0.5, y = spawn.getY(), z = spawn.getZ() + 0.5;

        player.teleportTo(world, x, y, z, Set.of(), yaw, 0.0F, true);
    }

    public void useNoteBlock(ServerPlayer player, BlockPos pos, DynamicEntityManager manager) {
        int index = getNoteBlock(pos);

        if (index == -1) return;

        int transpose = player.isShiftKeyDown() ? -1 : 1;

        setNote(index, notes[index] + transpose);

        playNote(player, index);
        removeDisplay(index, manager);
    }

    public void playNoteBlock(ServerPlayer player, BlockPos pos) {
        int index = getNoteBlock(pos);

        if (index == -1) return;

        playNote(player, index);
    }

    public void playNote(ServerPlayer player, int index) {
        nbPlayer.play(player, index);
    }

    public void setNote(int i, int note) {
        notes[i] = Math.floorMod(note, 25);
    }

    public int getNoteBlock(BlockPos pos) {
        int index = -1;

        for (int i = 0, noteBlocksLength = noteBlocks.length; i < noteBlocksLength; i++) {
            BlockPos noteBlock = noteBlocks[i];

            if (noteBlock.equals(pos)) {
                index = i;
                break;
            }
        }

        return index;
    }

    public void setTemporaryMelody(Melody melody) {
        System.arraycopy(notes, 0, tmpNotes, 0, notes.length);
        temporary = true;

        setMelody(melody);
    }

    public void restoreMelody() {
        if (!temporary) return;

        System.arraycopy(tmpNotes, 0, notes, 0, tmpNotes.length);
        temporary = false;
    }

    public void setMelody(Melody melody) {
        nbPlayer.setMelody(melody);
    }

    public Melody getCurrentMelody() {
        restoreMelody();

        NoteBlockInstrument instrument = instruments[0];
        Note[] currentNotes = new Note[notes.length];

        var allNotes = Note.values();

        for (int i = 0; i < currentNotes.length; i++) {
            currentNotes[i] = allNotes[notes[i]];
        }

        return new Melody(instrument, currentNotes);
    }

    public int calculateScore(Melody baseMelody, Melody reference) {
        restoreMelody();

        Note[] refNotes = reference.notes();
        int score = 0;

        for (int i = 0; i < notes.length; i++) {
            int actual = notes[i];
            int expected = refNotes[i].ordinal();
            int base = baseMelody.notes()[i].ordinal();

            int offset = abs(expected - base);
            int diff = abs(expected - actual);

            int noteScore = max(0, offset - diff);

            score += noteScore;
        }

        return score;
    }

    public boolean isComplete(Melody reference) {
        restoreMelody();

        Note[] refNotes = reference.notes();

        for (int i = 0; i < notes.length; i++) {
            int actual = notes[i];
            int expected = refNotes[i].ordinal();

            if (actual != expected) {
                return false;
            }
        }

        return true;
    }

    public void markErrors(Melody baseMelody, Melody reference, DynamicEntityManager manager, ServerPlayer player) {
        removeDisplays(manager);

        restoreMelody();

        Note[] refNotes = reference.notes();

        for (int i = 0; i < notes.length; i++) {
            int actual = notes[i];
            int expected = refNotes[i].ordinal();
            int base = baseMelody.notes()[i].ordinal();

            int offset = abs(expected - base);
            int diff = abs(expected - actual);

            if (diff == 0 || offset == 0) continue;

            float error = (float) diff / offset;
            addDisplay(i, error, manager, player);
        }
    }

    public void addDisplay(int note, float error, DynamicEntityManager manager, ServerPlayer viewer) {
        if (note < 0 || note >= displays.size()) return;

        BlockPos pos = noteBlocks[note];
        float margin = 0.015f;

        var display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, viewer.level());
        display.setPosRaw(pos.getX() + margin, pos.getY() + margin, pos.getZ() + margin);
        display.setBlockState(Blocks.NOTE_BLOCK.defaultBlockState());
        display.setTransformation(new Transformation(new Matrix4f().scale(1 - margin * 2)));
        display.setGlowingTag(true);

        int color = ColorUtil.lerpRgb(0xefe409, 0x890404, error);
        display.setGlowColorOverride(color);

        var dynamic = new PlayerSpecificDynamicEntity<>(display, viewer.getUUID());

        displays.set(note, dynamic);
        manager.add(dynamic);
    }

    public void removeDisplay(int note, DynamicEntityManager manager) {
        if (note < 0 || note >= displays.size()) return;

        var display = displays.get(note);

        if (display == null) return;

        displays.set(note, null);
        manager.remove(display);
    }

    public void removeDisplays(DynamicEntityManager manager) {
        for (int i = 0; i < displays.size(); i++) {
            var display = displays.get(i);

            if (display == null) continue;

            manager.remove(display);
            displays.set(i, null);
        }
    }
}

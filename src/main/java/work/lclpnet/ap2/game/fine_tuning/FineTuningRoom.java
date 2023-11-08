package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.enums.Instrument;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.game.fine_tuning.melody.Melody;
import work.lclpnet.ap2.game.fine_tuning.melody.Note;
import work.lclpnet.ap2.impl.util.SoundHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class FineTuningRoom {

    private final ServerWorld world;
    private final BlockPos[] noteBlocks;
    private final BlockPos spawn;
    private final float yaw;
    private final int[] notes, tmpNotes;
    private final List<Melody> melodies = new ArrayList<>(3);
    private boolean temporary = false;

    public FineTuningRoom(ServerWorld world, BlockPos pos, Vec3i[] relNoteBlock, Vec3i relSpawn, float yaw) {
        this.world = world;

        BlockPos[] noteBlocks = new BlockPos[relNoteBlock.length];

        for (int j = 0; j < relNoteBlock.length; j++) {
            noteBlocks[j] = pos.add(relNoteBlock[j]);
        }

        this.noteBlocks = noteBlocks;

        this.notes = new int[noteBlocks.length];
        Arrays.fill(notes, 0);

        this.tmpNotes = new int[noteBlocks.length];
        Arrays.fill(tmpNotes, 0);

        this.spawn = pos.add(relSpawn);
        this.yaw = yaw;
    }

    public void teleport(ServerPlayerEntity player) {
        double x = spawn.getX() + 0.5, y = spawn.getY(), z = spawn.getZ() + 0.5;

        player.teleport(world, x, y, z, yaw, 0.0F);
    }

    public void useNoteBlock(ServerPlayerEntity player, BlockPos pos) {
        int index = getNoteBlock(pos);
        if (index == -1) return;

        int transpose = player.isSneaking() ? -1 : 1;

        setNote(index, notes[index] + transpose);

        playNote(player, index);
    }

    public void playNoteBlock(ServerPlayerEntity player, BlockPos pos) {
        int index = getNoteBlock(pos);
        if (index == -1) return;

        playNote(player, index);
    }

    public void playNote(ServerPlayerEntity player, int index) {
        int note = notes[index];
        BlockPos pos = noteBlocks[index];
        BlockState state = world.getBlockState(pos);

        if (!state.isOf(Blocks.NOTE_BLOCK)) return;

        Instrument instrument = state.get(Properties.INSTRUMENT);
        float pitch;

        if (instrument.shouldSpawnNoteParticles()) {
            pitch = NoteBlock.getNotePitch(note);

            world.spawnParticles(player, ParticleTypes.NOTE, false,
                    pos.getX() + 0.5d,
                    pos.getY() + 1.2d,
                    pos.getZ() + 0.5d,
                    0,
                    note / 24.0d,
                    0.0,
                    0.0,
                    1);
        } else {
            pitch = 1.0f;
        }

        SoundHelper.playSound(player, instrument.getSound().value(), SoundCategory.RECORDS,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3f, pitch);
    }

    public void setNote(int i, int note) {
        notes[i] = Math.floorMod(note, 25);
    }

    private int getNoteBlock(BlockPos pos) {
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
        Note[] notes = melody.notes();
        BlockState state = Blocks.NOTE_BLOCK.getDefaultState()
                .with(NoteBlock.INSTRUMENT, melody.instrument());

        int i;

        for (i = 0; i < noteBlocks.length; i++) {
            if (i < notes.length) {
                this.notes[i] = notes[i].ordinal();
            } else {
                this.notes[i] = 0;
            }

            BlockPos pos = noteBlocks[i];
            world.setBlockState(pos, state);
        }
    }

    public Melody getCurrentMelody() {
        restoreMelody();

        Instrument instrument = world.getBlockState(noteBlocks[0]).get(NoteBlock.INSTRUMENT);
        Note[] currentNotes = new Note[notes.length];

        var allNotes = Note.values();

        for (int i = 0; i < currentNotes.length; i++) {
            currentNotes[i] = allNotes[notes[i]];
        }

        return new Melody(instrument, currentNotes);
    }

    public void saveMelody() {
        melodies.add(getCurrentMelody());
    }

    public int calculateScore(Melody reference) {
        restoreMelody();

        final int maxUnitScore = Note.values().length - 1;
        int score = 0;

        for (int i = 0; i < notes.length; i++) {
            int diff = Math.abs(reference.notes()[i].ordinal() - notes[i]);
            score += maxUnitScore - diff;
        }

        return score;
    }
}

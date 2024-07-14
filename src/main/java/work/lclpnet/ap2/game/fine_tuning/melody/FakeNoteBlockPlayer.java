package work.lclpnet.ap2.game.fine_tuning.melody;

import net.minecraft.block.NoteBlock;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.SoundHelper;

public class FakeNoteBlockPlayer {

    private final BlockPos[] noteBlocks;
    private final int[] notes;
    private final NoteBlockInstrument[] instruments;

    public FakeNoteBlockPlayer(BlockPos[] noteBlocks, int[] notes, NoteBlockInstrument[] instruments) {
        this.noteBlocks = noteBlocks;
        this.notes = notes;
        this.instruments = instruments;
    }

    public void play(ServerPlayerEntity player, int index) {
        BlockPos pos = noteBlocks[index];
        playAt(player, index, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public void playAtPlayerPos(ServerPlayerEntity player, int index) {
        playAt(player, index, player.getX(), player.getY(), player.getZ());
    }

    private void playAt(ServerPlayerEntity player, int index, double x, double y, double z) {
        int note = notes[index];
        BlockPos pos = noteBlocks[index];

        NoteBlockInstrument instrument = instruments[index];
        float pitch;

        if (instrument.canBePitched()) {
            pitch = NoteBlock.getNotePitch(note);

            player.getServerWorld().spawnParticles(player, ParticleTypes.NOTE, false,
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

        SoundHelper.playSound(player, instrument.getSound().value(), SoundCategory.RECORDS, x, y, z, 3f, pitch);
    }

    public void setMelody(Melody melody) {
        Note[] melodyNotes = melody.notes();
        NoteBlockInstrument instrument = melody.instrument();

        int i;

        for (i = 0; i < notes.length; i++) {
            if (i < melodyNotes.length) {
                notes[i] = melodyNotes[i].ordinal();
            } else {
                notes[i] = 0;
            }

            instruments[i] = instrument;
        }
    }
}

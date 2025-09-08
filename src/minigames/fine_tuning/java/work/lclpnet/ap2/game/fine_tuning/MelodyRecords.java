package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.game.fine_tuning.melody.Melody;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;

import java.util.ArrayList;
import java.util.List;

import static work.lclpnet.ap2.game.fine_tuning.FineTuningInstance.MELODY_COUNT;

class MelodyRecords {

    private final List<Entry> entries = new ArrayList<>();
    private final Melody[] melodies = new Melody[MELODY_COUNT];
    private int melodyNumber = 0;

    public void record(Melody reference, ServerPlayerEntity best, Melody bestMelody, ServerPlayerEntity worst, Melody worstMelody) {
        entries.add(new Entry(
                MelodyEntry.create(best, bestMelody, reference),
                MelodyEntry.create(worst, worstMelody, reference)
        ));
    }

    public void recordMelody(Melody melody) {
        melodies[melodyNumber++] = melody;
    }

    public Melody getMelody(int melodyNumber) {
        return melodies[melodyNumber];
    }

    public MelodyEntry getBestMelody(int melodyNumber) {
        return entries.get(melodyNumber).best();
    }

    public MelodyEntry getWorstMelody(int melodyNumber) {
        return entries.get(melodyNumber).worst();
    }

    public record Entry(MelodyEntry best, MelodyEntry worst) {}

    public record MelodyEntry(PlayerRef playerRef, Melody melody, int[] offsets) {

        static MelodyEntry create(ServerPlayerEntity player, Melody melody, Melody reference) {
            int[] offsets = new int[reference.notes().length];

            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = melody.notes()[i].ordinal() - reference.notes()[i].ordinal();
            }

            return new MelodyEntry(PlayerRef.create(player), melody, offsets);
        }
    }
}

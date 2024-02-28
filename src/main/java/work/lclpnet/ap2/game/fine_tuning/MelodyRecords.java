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

    public void record(ServerPlayerEntity best, Melody bestMelody, ServerPlayerEntity worst, Melody worstMelody) {
        entries.add(new Entry(
                new MelodyEntry(PlayerRef.create(best), bestMelody),
                new MelodyEntry(PlayerRef.create(worst), worstMelody)
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
    public record MelodyEntry(PlayerRef playerRef, Melody melody) {}
}

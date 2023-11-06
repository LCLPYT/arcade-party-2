package work.lclpnet.ap2.game.fine_tuning.melody;

import net.minecraft.block.enums.Instrument;

public class DummyMelodyProvider implements MelodyProvider {

    @Override
    public Melody nextMelody() {
        return new Melody(Instrument.BANJO, new Note[] {
                Note.FIS3, Note.A3, Note.AIS3, Note.C4, Note.D4
        });
    }
}

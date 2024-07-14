package work.lclpnet.ap2.game.fine_tuning.melody;

import net.minecraft.block.enums.NoteBlockInstrument;

public record Melody(NoteBlockInstrument instrument, Note[] notes) {

}

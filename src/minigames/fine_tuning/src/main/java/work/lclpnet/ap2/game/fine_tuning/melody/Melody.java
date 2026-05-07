package work.lclpnet.ap2.game.fine_tuning.melody;

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public record Melody(NoteBlockInstrument instrument, Note[] notes) {

}

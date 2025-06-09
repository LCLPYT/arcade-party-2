package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.util.Printable;
import work.lclpnet.ap2.impl.util.BlockBox;

public sealed interface JumpPart extends Printable permits Bridge, OrientedPart {

    BlockBox bounds();

    JumpPart transform(BlockPos offset);
}

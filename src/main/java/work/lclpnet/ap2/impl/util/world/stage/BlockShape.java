package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;

public interface BlockShape extends Iterable<BlockPos> {

    BlockPos origin();

    BlockPos center();

    boolean contains(BlockPos pos);

    BlockBox bounds();

    interface WithRadius {
        int radius();
    }

    interface WithHeight {
        int height();
    }
}

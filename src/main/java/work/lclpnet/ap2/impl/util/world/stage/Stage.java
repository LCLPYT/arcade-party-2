package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;

public interface Stage extends Iterable<BlockPos> {

    BlockPos getOrigin();

    BlockPos getCenter();

    boolean contains(BlockPos pos);

    interface WithRadius {
        int getRadius();
    }

    interface WithHeight {
        int getHeight();
    }
}

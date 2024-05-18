package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;

import java.util.Iterator;

public interface Stage {

    BlockPos getOrigin();

    Iterator<BlockPos> groundPositionIterator();

    BlockPos getCenter();

    int getRadius();

    int getHeight();

    boolean contains(BlockPos pos);
}

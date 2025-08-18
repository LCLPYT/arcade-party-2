package work.lclpnet.ap2.api.util.world;

import net.minecraft.util.math.BlockPos;

import java.util.Iterator;

/**
 * Provides adjacent blocks of a block.
 */
public interface AdjacentBlocks {

    Iterator<BlockPos> getAdjacent(BlockPos pos);

    default Iterable<BlockPos> iterate(BlockPos pos) {
        return () -> getAdjacent(pos);
    }
}

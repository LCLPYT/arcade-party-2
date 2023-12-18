package work.lclpnet.ap2.api.util.world;

import net.minecraft.util.math.BlockPos;

import java.util.Iterator;

public interface WorldScanner {

    Iterator<BlockPos> scan(BlockPos start);
}

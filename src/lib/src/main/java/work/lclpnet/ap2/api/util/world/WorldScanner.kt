package work.lclpnet.ap2.api.util.world;

import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.Set;

public interface WorldScanner {

    Iterator<BlockPos> scan(Set<BlockPos> starts);

    default Iterator<BlockPos> scan(BlockPos start) {
        return scan(Set.of(start));
    }
}

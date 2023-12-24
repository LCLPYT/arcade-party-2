package work.lclpnet.ap2.api.util.world;

import net.minecraft.util.math.BlockPos;

public interface BlockPredicate {

    boolean test(BlockPos pos);

    static BlockPredicate and(BlockPredicate first, BlockPredicate second) {
        return pos -> first.test(pos) && second.test(pos);
    }
}

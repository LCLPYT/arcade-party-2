package work.lclpnet.ap2.api.util.world;

import net.minecraft.util.math.BlockPos;

public interface BlockPredicate {

    boolean test(BlockPos pos);

    default BlockPredicate and(BlockPredicate other) {
        return pos -> this.test(pos) && other.test(pos);
    }

    static BlockPredicate and(BlockPredicate first, BlockPredicate second) {
        return first.and(second);
    }
}

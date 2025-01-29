package work.lclpnet.ap2.impl.util.world;

import com.google.common.collect.Iterables;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public class PositionUtil {

    public static Iterable<BlockPos> findGroundPositions(Iterable<BlockPos> pool, BlockView world) {
        return Iterables.filter(pool, new NotOccupiedBlockPredicate(world)::test);
    }
}

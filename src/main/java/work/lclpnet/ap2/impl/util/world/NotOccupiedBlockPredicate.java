package work.lclpnet.ap2.impl.util.world;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import work.lclpnet.ap2.api.util.world.BlockPredicate;

public class NotOccupiedBlockPredicate implements BlockPredicate {

    private final BlockView world;

    public NotOccupiedBlockPredicate(BlockView world) {
        this.world = world;
    }

    @Override
    public boolean test(BlockPos pos) {
        BlockState below = world.getBlockState(pos.down());

        return below.isSideSolidFullSquare(world, pos, Direction.UP) && isAir(pos) && isAir(pos.up());
    }

    private boolean isAir(BlockPos adj) {
        return world.getBlockState(adj).getCollisionShape(world, adj).isEmpty();
    }
}

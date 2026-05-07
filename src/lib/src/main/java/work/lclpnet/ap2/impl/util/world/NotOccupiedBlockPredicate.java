package work.lclpnet.ap2.impl.util.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import work.lclpnet.ap2.api.util.world.BlockPredicate;

public class NotOccupiedBlockPredicate implements BlockPredicate {

    private final BlockGetter world;

    public NotOccupiedBlockPredicate(BlockGetter world) {
        this.world = world;
    }

    @Override
    public boolean test(BlockPos pos) {
        BlockState below = world.getBlockState(pos.below());

        return below.isFaceSturdy(world, pos, Direction.UP) && isAir(pos) && isAir(pos.above());
    }

    private boolean isAir(BlockPos adj) {
        return world.getBlockState(adj).getCollisionShape(world, adj).isEmpty();
    }
}

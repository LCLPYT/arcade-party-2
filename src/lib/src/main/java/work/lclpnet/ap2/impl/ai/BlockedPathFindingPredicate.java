package work.lclpnet.ap2.impl.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ai.PathFindingPredicate;

public class BlockedPathFindingPredicate implements PathFindingPredicate {

    private BlockedPathFindingPredicate() {}

    @Override
    public boolean canReach(int x, int y, int z, Mob entity, BlockPos from) {
        Level world = entity.level();
        var to = new BlockPos(x, y, z);
        var prev = new BlockPos.MutableBlockPos();

        int dx = x - from.getX();
        int dz = z - from.getZ();

        Direction dir = Direction.getNearest(dx, 0, dz, null);

        if (dir != null) {
            return !isBidiBlocked(world, to, dir, prev, entity);
        }

        // check horizontal diagonal
        if (Math.abs(dx) != 1 || Math.abs(dz) != 1) {
            return true;
        }

        // check x direction first
        dir = Direction.getNearest(dx, 0, 0, null);

        if (dir != null && isBidiBlocked(world, to, dir, prev, entity)) {
            return false;
        }

        // then check z direction
        dir = Direction.getNearest(0, 0, dz, null);

        return dir == null || !isBidiBlocked(world, to, dir, prev, entity);
    }


    private boolean isBidiBlocked(Level world, BlockPos to, Direction dir, BlockPos.MutableBlockPos from, Mob entity) {
        from.set(to.getX() - dir.getStepX(),
                to.getY() - dir.getStepY(),
                to.getZ() - dir.getStepZ());

        // check if current position is blocked
        if (isBlockedByTrapdoor(world, to, dir, from, entity)) {
            return true;
        }

        // check if the previous position is blocked
        return isBlockedByTrapdoor(world, from, dir.getOpposite(), null, entity);
    }

    private boolean isBlockedByTrapdoor(Level world, BlockPos pos, Direction dir, @Nullable BlockPos from, Mob entity) {
        BlockState state = world.getBlockState(pos);

        if (!state.is(BlockTags.TRAPDOORS) || !state.hasProperty(TrapDoorBlock.FACING)
            || !state.hasProperty(TrapDoorBlock.OPEN) || !state.getValue(TrapDoorBlock.OPEN)
            || state.getValue(TrapDoorBlock.FACING) != dir) {
            return false;
        }

        // direct way is blocked by trapdoor, check if there is space to jump over the trapdoor
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        var blockCollisions = world.getBlockCollisions(entity, box);

        if (blockCollisions.iterator().hasNext()) {
            return true;
        }

        if (from == null) {
            return false;
        }

        // make sure the position from where to jump is safe
        BlockPos jumpSurface = from.below();
        state = world.getBlockState(jumpSurface);

        return !state.isFaceSturdy(world, jumpSurface, Direction.UP);
    }

    public static BlockedPathFindingPredicate getInstance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final BlockedPathFindingPredicate instance = new BlockedPathFindingPredicate();
    }
}

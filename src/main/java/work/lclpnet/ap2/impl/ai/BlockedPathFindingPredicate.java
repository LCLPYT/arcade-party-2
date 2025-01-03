package work.lclpnet.ap2.impl.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ai.PathFindingPredicate;

public class BlockedPathFindingPredicate implements PathFindingPredicate {

    private BlockedPathFindingPredicate() {}

    @Override
    public boolean canReach(int x, int y, int z, MobEntity entity, BlockPos from) {
        World world = entity.getWorld();
        var to = new BlockPos(x, y, z);
        var prev = new BlockPos.Mutable();

        int dx = x - from.getX();
        int dz = z - from.getZ();

        Direction dir = Direction.fromVector(dx, 0, dz, null);

        if (dir != null) {
            return !isBidiBlocked(world, to, dir, prev, entity);
        }

        // check horizontal diagonal
        if (Math.abs(dx) != 1 || Math.abs(dz) != 1) {
            return true;
        }

        // check x direction first
        dir = Direction.fromVector(dx, 0, 0, null);

        if (dir != null && isBidiBlocked(world, to, dir, prev, entity)) {
            return false;
        }

        // then check z direction
        dir = Direction.fromVector(0, 0, dz, null);

        return dir == null || !isBidiBlocked(world, to, dir, prev, entity);
    }


    private boolean isBidiBlocked(World world, BlockPos to, Direction dir, BlockPos.Mutable from, MobEntity entity) {
        from.set(to.getX() - dir.getOffsetX(),
                to.getY() - dir.getOffsetY(),
                to.getZ() - dir.getOffsetZ());

        // check if current position is blocked
        if (isBlockedByTrapdoor(world, to, dir, from, entity)) {
            return true;
        }

        // check if the previous position is blocked
        return isBlockedByTrapdoor(world, from, dir.getOpposite(), null, entity);
    }

    private boolean isBlockedByTrapdoor(World world, BlockPos pos, Direction dir, @Nullable BlockPos from, MobEntity entity) {
        BlockState state = world.getBlockState(pos);

        if (!state.isIn(BlockTags.TRAPDOORS) || !state.contains(TrapdoorBlock.FACING)
            || !state.contains(TrapdoorBlock.OPEN) || !state.get(TrapdoorBlock.OPEN)
            || state.get(TrapdoorBlock.FACING) != dir) {
            return false;
        }

        // direct way is blocked by trapdoor, check if there is space to jump over the trapdoor
        Box box = entity.getDimensions(entity.getPose()).getBoxAt(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        var blockCollisions = world.getBlockCollisions(entity, box);

        if (blockCollisions.iterator().hasNext()) {
            return true;
        }

        if (from == null) {
            return false;
        }

        // make sure the position from where to jump is safe
        BlockPos jumpSurface = from.down();
        state = world.getBlockState(jumpSurface);

        return !state.isSideSolidFullSquare(world, jumpSurface, Direction.UP);
    }

    public static BlockedPathFindingPredicate getInstance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final BlockedPathFindingPredicate instance = new BlockedPathFindingPredicate();
    }
}

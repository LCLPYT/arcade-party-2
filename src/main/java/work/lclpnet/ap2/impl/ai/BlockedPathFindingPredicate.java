package work.lclpnet.ap2.impl.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import work.lclpnet.ap2.api.ai.PathFindingPredicate;

public class BlockedPathFindingPredicate implements PathFindingPredicate {

    private BlockedPathFindingPredicate() {}

    @Override
    public boolean canReach(int x, int y, int z, MobEntity entity, BlockPos from) {
        World world = entity.getWorld();
        var pos = new BlockPos.Mutable(x, y, z);

        int dx = x - from.getX();
        int dz = z - from.getZ();

        Direction dir = Direction.fromVector(dx, 0, dz);

        if (dir != null) {
            return !isBidiBlocked(world, pos, dir);
        }

        // check horizontal diagonal
        if (Math.abs(dx) != 1 || Math.abs(dz) != 1) {
            return true;
        }

        // check x direction first
        dir = Direction.fromVector(dx, 0, 0);

        if (dir != null && isBidiBlocked(world, pos, dir)) {
            return false;
        }

        // then check z direction
        dir = Direction.fromVector(0, 0, dz);
        pos.set(x, y, z);  // reset pos as it was likely modified

        return dir == null || !isBidiBlocked(world, pos, dir);
    }


    private boolean isBidiBlocked(World world, BlockPos.Mutable pos, Direction dir) {
        // check if current position is blocked
        if (isBlockedByTrapdoor(world, pos, dir)) {
            return true;
        }

        // check if the previous position is blocked
        pos.set(pos.getX() - dir.getOffsetX(),
                pos.getY() - dir.getOffsetY(),
                pos.getZ() - dir.getOffsetZ());

        return isBlockedByTrapdoor(world, pos, dir.getOpposite());
    }

    private boolean isBlockedByTrapdoor(World world, BlockPos pos, Direction dir) {
        BlockState state = world.getBlockState(pos);

        if (state.isIn(BlockTags.TRAPDOORS) && state.contains(TrapdoorBlock.FACING) && state.contains(TrapdoorBlock.OPEN)) {
            return state.get(TrapdoorBlock.OPEN) && state.get(TrapdoorBlock.FACING) == dir;
        }

        return false;
    }

    public static BlockedPathFindingPredicate getInstance() {
        return Holder.instance;
    }

    private static class Holder {
        private static final BlockedPathFindingPredicate instance = new BlockedPathFindingPredicate();
    }
}

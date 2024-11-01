package work.lclpnet.ap2.core.patch;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class BlockedPathfindingPatch {

    private BlockedPathfindingPatch() {}

    /**
     * Additional neighbour checks for the A* pathfinding of select entities.
     * @param x The x of the block.
     * @param y The y of the block.
     * @param z The z of the block.
     * @param entity The entity
     * @param from The parent position in the path to be constructed.
     * @return Whether the way from the previous position towards the next is blocked.
     */
    public static boolean isBlocked(int x, int y, int z, MobEntity entity, BlockPos from) {
        World world = entity.getWorld();
        var pos = new BlockPos.Mutable(x, y, z);

        int dx = from.getX() - x;
        int dy = from.getY() - y;
        int dz = from.getZ() - z;

        Direction dir = Direction.fromVector(dx, dy, dz);

        if (dir != null) {
            return isBidiBlocked(world, pos, dir);
        }

        // check horizontal diagonal
        if (dy != 0 || Math.abs(dx) != 1 || Math.abs(dz) != 1) {
            return false;
        }

        // check x direction first
        dir = Direction.fromVector(dx, 0, 0);

        if (dir != null && isBidiBlocked(world, pos, dir)) {
            return true;
        }

        // then check z direction
        dir = Direction.fromVector(0, 0, -dz);
        pos.set(x, y, z);  // reset pos as it was likely modified

        return dir != null && isBidiBlocked(world, pos, dir);
    }

    private static boolean isBidiBlocked(World world, BlockPos.Mutable pos, Direction dir) {
        // check if current position is blocked
        if (isBlockedByTrapdoor(world, pos, dir)) {
            return true;
        }

        // check if the previous position is blocked
        pos.set(pos.getX() + dir.getOffsetX(),
                pos.getY() + dir.getOffsetY(),
                pos.getZ() + dir.getOffsetZ());

        return isBlockedByTrapdoor(world, pos, dir);
    }

    private static boolean isBlockedByTrapdoor(World world, BlockPos pos, Direction dir) {
        BlockState state = world.getBlockState(pos);

        if (state.isIn(BlockTags.TRAPDOORS) && state.contains(TrapdoorBlock.FACING) && state.contains(TrapdoorBlock.OPEN)) {
            return state.get(TrapdoorBlock.OPEN) && state.get(TrapdoorBlock.FACING) == dir;
        }

        return false;
    }
}

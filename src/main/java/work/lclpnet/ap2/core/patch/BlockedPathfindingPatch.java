package work.lclpnet.ap2.core.patch;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BlockedPathfindingPatch {

    private BlockedPathfindingPatch() {}

    /**
     * Additional neighbour checks for the A* pathfinding of select entities.
     * @param x The x of the block.
     * @param y The y of the block.
     * @param z The z of the block.
     * @param entity The entity
     * @param direction The direction in which the neighbour check occurs (-direction points towards the predecessor).
     * @param pathNodeType The path node type of the block at (x,y,z).
     * @return Whether the way from <code>(x,y,z) - direction</code> to <code>(x,y,z)</code> is blocked.
     */
    public static boolean isBlocked(int x, int y, int z, MobEntity entity, Direction direction, PathNodeType pathNodeType) {
        if (pathNodeType != PathNodeType.DANGER_TRAPDOOR) return false;

        BlockState state = entity.getWorld().getBlockState(new BlockPos(x, y, z));

        if (state.isIn(BlockTags.TRAPDOORS) && state.contains(TrapdoorBlock.FACING) && state.contains(TrapdoorBlock.OPEN)) {
            return state.get(TrapdoorBlock.OPEN) && state.get(TrapdoorBlock.FACING) == direction;
        }

        return false;
    }
}

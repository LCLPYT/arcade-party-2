package work.lclpnet.ap2.api.ai;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;

public interface PathFindingPredicate {

    /**
     * Checks whether a block can be reached from another block.
     * This influences the A* pathfinding algorithm of configured entities.
     * @param x The x of the block.
     * @param y The y of the block.
     * @param z The z of the block.
     * @param entity The entity
     * @param from The parent position in the path to be constructed.
     * @return Whether the way from the previous position towards the next is passable by the entity.
     */
    boolean canReach(int x, int y, int z, MobEntity entity, BlockPos from);
}

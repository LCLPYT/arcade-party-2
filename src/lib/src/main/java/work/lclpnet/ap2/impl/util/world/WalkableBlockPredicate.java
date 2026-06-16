package work.lclpnet.ap2.impl.util.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import work.lclpnet.ap2.api.util.world.BlockPredicate;

import static net.minecraft.core.Direction.Axis.*;

public class WalkableBlockPredicate implements BlockPredicate {

    private final BlockGetter world;
    private final int verticalSpace;

    public WalkableBlockPredicate(BlockGetter world) {
        this(world, 2);
    }

    public WalkableBlockPredicate(BlockGetter world, int verticalSpace) {
        this.world = world;
        this.verticalSpace = verticalSpace;
    }

    @Override
    public boolean test(BlockPos pos) {
        // verify position itself is free; in addition to passable blocks, very low collision boxes
        // (e.g. carpets, pressure plates) can also be stood in
        VoxelShape selfShape = world.getBlockState(pos).getCollisionShape(world, pos, CollisionContext.empty());

        if (!isPassable(selfShape) && selfShape.max(Y) - selfShape.min(Y) > 0.5) {
            return false;
        }

        // verify position below is solid
        var queryPos = new BlockPos.MutableBlockPos(pos.getX(), pos.getY() - 1, pos.getZ());

        BlockState state = world.getBlockState(queryPos);

        if (state.is(Blocks.LADDER)) {
            return false;
        }

        VoxelShape shape = state.getCollisionShape(world, queryPos);

        if (shape.isEmpty()) {
            return false;
        }

        double maxY = shape.max(Y);

        if (maxY < 0.8 || maxY - shape.min(Y) > 1 || length(shape, X) < 0.4 || length(shape, Z) < 0.4) {
            return false;
        }

        // verify position above is free
        for (int i = 1; i < verticalSpace; i++) {
            queryPos.setY(pos.getY() + 1);

            shape = world.getBlockState(queryPos).getCollisionShape(world, queryPos);

            if (shape.isEmpty()) continue;

            if (length(shape, X) >= 0.4 && length(shape, Z) >= 0.4) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests whether a position is open enough to move through, i.e. it is air or has only a slim
     * collision box along the x or z axis (such as doors). The block height is not considered.
     * This does not consider the blocks above or below the position.
     */
    public static boolean isPassable(BlockGetter world, BlockPos pos) {
        return isPassable(world.getBlockState(pos).getCollisionShape(world, pos, CollisionContext.empty()));
    }

    private static boolean isPassable(VoxelShape shape) {
        if (shape.isEmpty()) {
            return true;
        }

        double minX = shape.min(X), maxX = shape.max(X);
        double minZ = shape.min(Z), maxZ = shape.max(Z);

        // support slim blocks like doors
        boolean spaceX = maxX - minX <= 0.4 && (isClose(minX, 0) || isClose(maxX, 1));
        boolean spaceZ = maxZ - minZ <= 0.4 && (isClose(minZ, 0) || isClose(maxZ, 1));

        return spaceX || spaceZ;
    }

    private static double length(VoxelShape shape, Direction.Axis axis) {
        return shape.max(axis) - shape.min(axis);
    }

    private static boolean isClose(double a, double b) {
        return Math.abs(a - b) < 1e-4f;
    }
}

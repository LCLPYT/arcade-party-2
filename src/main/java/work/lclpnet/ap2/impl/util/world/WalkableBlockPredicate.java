package work.lclpnet.ap2.impl.util.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import work.lclpnet.ap2.api.util.world.BlockPredicate;

import static net.minecraft.util.math.Direction.Axis.*;

public class WalkableBlockPredicate implements BlockPredicate {

    private final BlockView world;

    public WalkableBlockPredicate(BlockView world) {
        this.world = world;
    }

    @Override
    public boolean test(BlockPos pos) {
        // verify position itself is free
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.absent());

        if (!shape.isEmpty()) {
            double minX = shape.getMin(X), maxX = shape.getMax(X);
            double minY = shape.getMin(Y), maxY = shape.getMax(Y);
            double minZ = shape.getMin(Z), maxZ = shape.getMax(Z);

            // support slim blocks like doors
            boolean spaceX = maxX - minX <= 0.4 && (isClose(minX, 0) || isClose(maxX, 1));
            boolean spaceZ = maxZ - minZ <= 0.4 && (isClose(minZ, 0) || isClose(maxZ, 1));

            if (!spaceX && !spaceZ && maxY - minY > 0.5) {
                return false;
            }
        }

        // verify position below is solid
        var queryPos = new BlockPos.Mutable(pos.getX(), pos.getY() - 1, pos.getZ());

        state = world.getBlockState(queryPos);

        if (state.isOf(Blocks.LADDER)) {
            return false;
        }

        shape = state.getCollisionShape(world, queryPos);

        if (shape.isEmpty()) {
            return false;
        }

        double maxY = shape.getMax(Y);

        if (maxY < 0.8 || maxY - shape.getMin(Y) > 1 || length(shape, X) < 0.4 || length(shape, Z) < 0.4) {
            return false;
        }

        // verify position above is free
        queryPos.setY(pos.getY() + 1);

        shape = world.getBlockState(queryPos).getCollisionShape(world, queryPos);

        if (shape.isEmpty()) {
            return true;
        }

        return length(shape, X) < 0.4 || length(shape, Z) < 0.4;
    }

    private static double length(VoxelShape shape, Direction.Axis axis) {
        return shape.getMax(axis) - shape.getMin(axis);
    }

    private static boolean isClose(double a, double b) {
        return Math.abs(a - b) < 1e-4f;
    }
}

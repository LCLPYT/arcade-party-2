package work.lclpnet.ap2.impl.util.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import work.lclpnet.ap2.api.util.world.BlockPredicate;

public class WalkableBlockPredicate implements BlockPredicate {

    private final BlockView world;

    public WalkableBlockPredicate(BlockView world) {
        this.world = world;
    }

    @Override
    public boolean test(BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.absent());

        if (!shape.isEmpty()) {
            Box box = shape.getBoundingBox();

            boolean spaceX = box.getLengthX() <= 0.4 && (isClose(box.minX, 0) || isClose(box.maxX, 1));
            boolean spaceZ = box.getLengthZ() <= 0.4 && (isClose(box.minZ, 0) || isClose(box.maxZ, 1));

            if (!spaceX && !spaceZ && box.maxY - box.minY > 0.5) {
                return false;
            }
        }

        BlockPos below = pos.down();
        BlockState stateBelow = world.getBlockState(below);

        if (stateBelow.isOf(Blocks.LADDER)) {
            return false;
        }

        VoxelShape shapeBelow = stateBelow.getCollisionShape(world, below);

        if (shapeBelow.isEmpty()) {
            return false;
        }

        Box boundsBelow = shapeBelow.getBoundingBox();

        if (boundsBelow.getLengthY() > 1 || boundsBelow.getLengthX() < 0.4 || boundsBelow.getLengthZ() < 0.4 || boundsBelow.maxY < 0.8) {
            return false;
        }

        BlockPos above = pos.up();
        VoxelShape shapeAbove = world.getBlockState(above).getCollisionShape(world, above);

        if (shapeAbove.isEmpty()) {
            return true;
        }

        Box boundsAbove = shapeAbove.getBoundingBox();

        return boundsAbove.getLengthX() < 0.4 || boundsAbove.getLengthZ() < 0.4;
    }

    private static boolean isClose(double a, double b) {
        return Math.abs(a - b) < 1e-4f;
    }
}

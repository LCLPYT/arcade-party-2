package work.lclpnet.ap2.impl.util.world.block_shape;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Collider;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public interface BlockShape extends Iterable<BlockPos>, Collider {

    BlockShapes.Type<?> type();

    BlockPos origin();

    BlockPos center();

    BlockBox bounds();

    boolean contains(double x, double y, double z);

    default boolean contains(int x, int y, int z) {
        return contains(x, y, (double) z);
    }

    default boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    default boolean contains(Position pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    default boolean collidesWith(double x, double y, double z) {
        return contains(x, y, z);
    }

    @Override
    default BlockPos min() {
        return bounds().min();
    }

    @Override
    default BlockPos max() {
        return bounds().max();
    }

    default BlockPos randomBlockPos(Random random) {
        final int maxTries = 100;

        BlockBox bounds = bounds();
        var mutable = new BlockPos.Mutable();

        for (int i = 0; i < maxTries; i++) {
            bounds.randomBlockPos(mutable, random);

            if (contains(mutable)) {
                return mutable;
            }
        }

        // fallback - collect all and choose randomly
        List<BlockPos> positions = new ArrayList<>();

        for (BlockPos pos : this) {
            positions.add(pos.toImmutable());
        }

        return positions.get(random.nextInt(positions.size()));
    }

    default Vec3d randomPos(Random random) {
        BlockPos p = randomBlockPos(random);

        return new Vec3d(p.getX() + random.nextDouble(), p.getY() + random.nextDouble(), p.getZ() + random.nextDouble());
    }

    interface WithRadius {
        int radius();
    }

    interface WithHeight {
        int height();
    }
}

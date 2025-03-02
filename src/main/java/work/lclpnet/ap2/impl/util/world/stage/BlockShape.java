package work.lclpnet.ap2.impl.util.world.stage;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.BlockBox;

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

    interface WithRadius {
        int radius();
    }

    interface WithHeight {
        int height();
    }
}

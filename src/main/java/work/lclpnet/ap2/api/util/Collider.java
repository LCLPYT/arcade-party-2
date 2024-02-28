package work.lclpnet.ap2.api.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;

public interface Collider {

    boolean collidesWith(double x, double y, double z);

    BlockPos getMin();

    BlockPos getMax();

    default boolean collidesWith(Position pos) {
        return collidesWith(pos.getX(), pos.getY(), pos.getZ());
    }

    default boolean collidesWith(BlockPos pos) {
        return collidesWith(pos.getX(), pos.getY(), pos.getZ());
    }
}

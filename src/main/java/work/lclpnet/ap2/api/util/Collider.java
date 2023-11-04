package work.lclpnet.ap2.api.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;

public interface Collider {

    boolean collidesWith(Position pos);

    BlockPos getMin();

    BlockPos getMax();
}

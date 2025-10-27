package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.gaco.ds.BlockBox;

public interface Shape {

    boolean contains(double x, double y, double z);

    /**
     * @return An axis-aligned bounding box containing the whole shape.
     */
    BlockBox bounds();

    Vec3d center();

    default void debug(DebugController controller) {
    }
}

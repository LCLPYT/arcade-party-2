package work.lclpnet.ap2.impl.util.math.shape;

import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.debug.DebugController;

public interface Shape {

    boolean contains(double x, double y, double z);

    /**
     * @return An axis-aligned bounding box containing the whole shape.
     */
    BlockBox bounds();

    /**
     * @param x The x position.
     * @param y The y position.
     * @param z The z position.
     * @return The double distance towards the shape center.
     */
    default double distance(double x, double y, double z) {
        return Shapes.chebyshevDelta(x, y, z);
    }

    default void debug(DebugController controller) {
    }
}

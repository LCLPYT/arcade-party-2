package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.BlockBox;

import static java.lang.Math.floor;

public interface SphereBoundedShape extends Shape {

    Vec3d center();

    double radius();

    @Override
    default BlockBox bounds() {
        Vec3d center = center();
        double radius = radius();

        return new BlockBox(
                (int) floor(center.getX() - radius), (int) floor(center.getY() - radius), (int) floor(center.getZ() - radius),
                (int) floor(center.getX() + radius), (int) floor(center.getY() + radius), (int) floor(center.getZ() + radius)
        );
    }
}

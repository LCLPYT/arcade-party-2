package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.world.phys.Vec3;
import work.lclpnet.gaco.ds.BlockBox;

import static java.lang.Math.floor;

public interface SphereBoundedShape extends Shape {

    Vec3 center();

    double radius();

    @Override
    default BlockBox bounds() {
        Vec3 center = center();
        double radius = radius();

        return new BlockBox(
                (int) floor(center.x() - radius), (int) floor(center.y() - radius), (int) floor(center.z() - radius),
                (int) floor(center.x() + radius), (int) floor(center.y() + radius), (int) floor(center.z() + radius)
        );
    }
}

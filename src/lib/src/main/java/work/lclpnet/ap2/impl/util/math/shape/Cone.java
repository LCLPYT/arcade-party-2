package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.world.phys.Vec3;
import work.lclpnet.gaco.ds.BlockBox;

import static java.lang.Math.floor;

public class Cone implements Shape {

    private final Vec3 origin;
    private final double radius, height;

    public Cone(Vec3 origin, double radius, double height) {
        this.origin = origin;
        this.radius = radius;
        this.height = height;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        if (y < origin.y() || y > origin.y() + height) {
            return false;
        }

        double yFraction = (y - origin.y()) / height;
        double currentRadius = radius * (1.0 - yFraction);

        double dx = x - origin.x();
        double dz = z - origin.z();

        return dx * dx + dz * dz < currentRadius * currentRadius;
    }

    @Override
    public BlockBox bounds() {
        return new BlockBox(
                (int) floor(origin.x() - radius), (int) floor(origin.y()), (int) floor(origin.z() - radius),
                (int) floor(origin.x() + radius), (int) floor(origin.y() + height), (int) floor(origin.z() + radius));
    }

    @Override
    public Vec3 center() {
        return origin.add(0, height / 2, 0);
    }
}

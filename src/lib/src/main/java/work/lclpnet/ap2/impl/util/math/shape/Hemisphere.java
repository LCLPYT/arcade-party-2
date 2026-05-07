package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.world.phys.Vec3;

public class Hemisphere implements SphereBoundedShape {

    private final Vec3 center;
    private final double radius;
    private final Vec3 normal;

    public Hemisphere(Vec3 center, double radius, Vec3 normal) {
        this.center = center;
        this.radius = radius;
        this.normal = normal;
    }

    @Override
    public Vec3 center() {
        return center;
    }

    @Override
    public double radius() {
        return radius;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        double dx = x - center.x();
        double dy = y - center.y();
        double dz = z - center.z();

        boolean inSphere = (dx * dx + dy * dy + dz * dz) < radius * radius;

        if (!inSphere) {
            return false;
        }

        return (dx * normal.x() + dy * normal.y() + dz * normal.z()) >= 0;
    }
}

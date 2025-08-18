package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;

public class Hemisphere implements SphereBoundedShape {

    private final Vec3d center;
    private final double radius;
    private final Vec3d normal;

    public Hemisphere(Vec3d center, double radius, Vec3d normal) {
        this.center = center;
        this.radius = radius;
        this.normal = normal;
    }

    @Override
    public Vec3d center() {
        return center;
    }

    @Override
    public double radius() {
        return radius;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        double dx = x - center.getX();
        double dy = y - center.getY();
        double dz = z - center.getZ();

        boolean inSphere = (dx * dx + dy * dy + dz * dz) < radius * radius;

        if (!inSphere) {
            return false;
        }

        return (dx * normal.getX() + dy * normal.getY() + dz * normal.getZ()) >= 0;
    }
}

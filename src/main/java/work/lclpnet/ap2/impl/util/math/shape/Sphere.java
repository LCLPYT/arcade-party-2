package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;

public class Sphere extends Ellipsoid implements SphereBoundedShape {

    private final double radius;

    public Sphere(Vec3d center, double radius) {
        super(center, radius, radius, radius);
        this.radius = radius;
    }

    @Override
    public double radius() {
        return radius;
    }
}

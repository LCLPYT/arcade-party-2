package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.world.phys.Vec3;
import work.lclpnet.gaco.ds.BlockBox;

import static java.lang.Math.floor;

public class Ellipsoid implements Shape {

    private final Vec3 center;
    private final double a, b, c;

    public Ellipsoid(Vec3 center, double a, double b, double c) {
        this.center = center;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        double dx = x - center.x();
        double dy = y - center.y();
        double dz = z - center.z();

        return (dx * dx) / (a * a) + (dy * dy) / (b * b) + (dz * dz) / (c * c) < 1.0;
    }

    @Override
    public BlockBox bounds() {
        return new BlockBox(
                (int) floor(center.x() - a), (int) floor(center.y() - b), (int) floor(center.z() - c),
                (int) floor(center.x() + a), (int) floor(center.y() + b), (int) floor(center.z() + c));
    }

    public Vec3 center() {
        return center;
    }
}

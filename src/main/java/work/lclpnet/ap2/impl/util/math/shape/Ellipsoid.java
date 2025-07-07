package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.BlockBox;

import static java.lang.Math.floor;

public class Ellipsoid implements Shape {

    private final Vec3d center;
    private final double a, b, c;

    public Ellipsoid(Vec3d center, double a, double b, double c) {
        this.center = center;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        double dx = x - center.getX();
        double dy = y - center.getY();
        double dz = z - center.getZ();

        return (dx * dx) / (a * a) + (dy * dy) / (b * b) + (dz * dz) / (c * c) < 1.0;
    }

    @Override
    public BlockBox bounds() {
        return new BlockBox(
                (int) floor(center.getX() - a), (int) floor(center.getY() - b), (int) floor(center.getZ() - c),
                (int) floor(center.getX() + a), (int) floor(center.getY() + b), (int) floor(center.getZ() + c));
    }

    public Vec3d center() {
        return center;
    }
}

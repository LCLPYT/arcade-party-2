package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.BlockBox;

import static java.lang.Math.*;

public class Torus implements Shape {

    private final Vec3d center;
    private final double majorRadius;
    private final double minorRadius;

    public Torus(Vec3d center, double majorRadius, double minorRadius) {
        this.center = center;
        this.majorRadius = majorRadius;
        this.minorRadius = minorRadius;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        double dx = x - center.getX();
        double dy = y - center.getY();
        double dz = z - center.getZ();

        double term = majorRadius - sqrt(dx * dx + dz * dz);

        return term * term + dy * dy < minorRadius * minorRadius;
    }

    @Override
    public BlockBox bounds() {
        double totalRadius = majorRadius + minorRadius;

        return new BlockBox(
                (int) floor(center.getX() - totalRadius), (int) floor(center.getY() - minorRadius), (int) floor(center.getZ() - totalRadius),
                (int) floor(center.getX() + totalRadius), (int) floor(center.getY() + minorRadius), (int) floor(center.getZ() + totalRadius));
    }

    @Override
    public Vec3d center() {
        return center;
    }

    public double majorRadius() {
        return majorRadius;
    }

    public double minorRadius() {
        return minorRadius;
    }
}

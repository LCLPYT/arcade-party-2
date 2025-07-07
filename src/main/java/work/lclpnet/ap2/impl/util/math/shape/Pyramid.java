package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.BlockBox;

import static java.lang.Math.abs;
import static java.lang.Math.floor;

public class Pyramid implements Shape {

    private final Vec3d origin;
    private final double radius;
    private final double height;

    public Pyramid(Vec3d origin, double radius, double height) {
        this.origin = origin;
        this.radius = radius;
        this.height = height;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        if (y < origin.getY() || y > origin.getY() + height) {
            return false;
        }

        double yFraction = (y - origin.getY()) / height;
        double currentRadius = radius * (1.0 - yFraction);

        return abs(x - origin.getX()) < currentRadius && abs(z - origin.getZ()) < currentRadius;
    }

    @Override
    public BlockBox bounds() {
        return new BlockBox(
                (int) floor(origin.getX() - radius), (int) floor(origin.getY()), (int) floor(origin.getZ() - radius),
                (int) floor(origin.getX() + radius), (int) floor(origin.getY() + height), (int) floor(origin.getZ() + radius));
    }

    @Override
    public Vec3d center() {
        return origin.add(0, height / 2, 0);
    }

    public Vec3d origin() {
        return origin;
    }

    public double height() {
        return height;
    }
}

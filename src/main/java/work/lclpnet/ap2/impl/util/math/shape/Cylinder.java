package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.BlockBox;

import static java.lang.Math.floor;

public class Cylinder implements Shape {

    private final Vec3d origin;
    private final double radius, height;

    public Cylinder(Vec3d origin, double radius, double height) {
        this.origin = origin;
        this.radius = radius;
        this.height = height;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        if (y < origin.getY() || y > origin.getY() + height) {
            return false;
        }

        double dx = x - origin.getX();
        double dz = z - origin.getZ();

        return dx * dx + dz * dz < radius * radius;
    }

    @Override
    public BlockBox bounds() {
        return new BlockBox(
                (int) floor(origin.getX() - radius), (int) floor(origin.getY()), (int) floor(origin.getZ() - radius),
                (int) floor(origin.getX() + radius), (int) floor(origin.getY() + height), (int) floor(origin.getZ() + radius));
    }
}

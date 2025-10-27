package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import work.lclpnet.gaco.ds.BlockBox;

import static java.lang.Math.floor;
import static java.lang.Math.sqrt;

public class Torus implements Shape {

    private final Vec3d center;
    private final double majorRadius;
    private final double minorRadius;
    private final Quaterniondc rotation;
    private final BlockBox bounds;

    public Torus(Vec3d center, double majorRadius, double minorRadius, Quaterniondc rotation) {
        this.center = center;
        this.majorRadius = majorRadius;
        this.minorRadius = minorRadius;
        this.rotation = rotation;
        this.bounds = calculateBounds();
    }

    private BlockBox calculateBounds() {
        Vector3d[] localCorners = localCorners();

        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);

        for (Vector3d corner : localCorners) {
            rotation.transform(corner);

            corner.add(center.getX(), center.getY(), center.getZ());

            min.min(corner);
            max.max(corner);
        }

        return new BlockBox(
                (int) floor(min.x), (int) floor(min.y), (int) floor(min.z),
                (int) floor(max.x), (int) floor(max.y), (int) floor(max.z)
        );
    }

    private Vector3d @NotNull [] localCorners() {
        double maxRadius = majorRadius + minorRadius;

        return new Vector3d[]{
                new Vector3d(-maxRadius, -minorRadius, -maxRadius),
                new Vector3d( maxRadius, -minorRadius, -maxRadius),
                new Vector3d( maxRadius,  minorRadius, -maxRadius),
                new Vector3d(-maxRadius,  minorRadius, -maxRadius),
                new Vector3d(-maxRadius, -minorRadius,  maxRadius),
                new Vector3d( maxRadius, -minorRadius,  maxRadius),
                new Vector3d( maxRadius,  minorRadius,  maxRadius),
                new Vector3d(-maxRadius,  minorRadius,  maxRadius)
        };
    }

    @Override
    public boolean contains(double x, double y, double z) {
        Vector3d local = new Vector3d(x - center.getX(), y - center.getY(), z - center.getZ());

        rotation.transformInverse(local);

        double dx = local.x;
        double dy = local.y;
        double dz = local.z;

        double term = majorRadius - sqrt(dx * dx + dz * dz);

        return term * term + dy * dy < minorRadius * minorRadius;
    }

    @Override
    public BlockBox bounds() {
        return bounds;
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

    public Quaterniondc rotation() {
        return rotation;
    }
}

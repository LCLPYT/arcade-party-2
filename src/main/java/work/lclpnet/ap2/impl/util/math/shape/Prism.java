package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.BlockBox;

import static java.lang.Math.*;

public class Prism implements Shape {

    private final Vec3d v1, v2, v3;
    private final double height;
    private final Vec3d direction;

    public Prism(Vec3d v1, Vec3d v2, Vec3d v3, double height, Vec3d direction) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.height = height;
        this.direction = direction;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        Vec3d p = new Vec3d(x, y, z);
        Vec3d diff = p.subtract(v1);

        double projHeight = diff.dotProduct(direction);

        if (projHeight < 0 || projHeight > height) {
            return false;
        }

        Vec3d proj = p.subtract(direction.multiply(projHeight));

        Vec3d edge1 = v2.subtract(v1);
        Vec3d edge2 = v3.subtract(v1);
        Vec3d pv = proj.subtract(v1);

        double d00 = edge1.dotProduct(edge1);
        double d01 = edge1.dotProduct(edge2);
        double d11 = edge2.dotProduct(edge2);
        double d20 = pv.dotProduct(edge1);
        double d21 = pv.dotProduct(edge2);

        // barycentric coordinates
        double d = d00 * d11 - d01 * d01;

        if (abs(d) < 1e-9) {
            return false;
        }

        double u = (d11 * d20 - d01 * d21) / d;
        double v = (d00 * d21 - d01 * d20) / d;

        return (u >= 0) && (v >= 0) && (u + v <= 1);
    }

    @Override
    public BlockBox bounds() {
        Vec3d topOffset = direction.multiply(height);
        Vec3d v1top = v1.add(topOffset);
        Vec3d v2top = v2.add(topOffset);
        Vec3d v3top = v3.add(topOffset);

        // Find the min and max coordinates among all 6 vertices
        double minX = min(v1.getX(), min(v2.getX(), min(v3.getX(), min(v1top.getX(), min(v2top.getX(), v3top.getX())))));
        double minY = min(v1.getY(), min(v2.getY(), min(v3.getY(), min(v1top.getY(), min(v2top.getY(), v3top.getY())))));
        double minZ = min(v1.getZ(), min(v2.getZ(), min(v3.getZ(), min(v1top.getZ(), min(v2top.getZ(), v3top.getZ())))));

        double maxX = max(v1.getX(), max(v2.getX(), max(v3.getX(), max(v1top.getX(), max(v2top.getX(), v3top.getX())))));
        double maxY = max(v1.getY(), max(v2.getY(), max(v3.getY(), max(v1top.getY(), max(v2top.getY(), v3top.getY())))));
        double maxZ = max(v1.getZ(), max(v2.getZ(), max(v3.getZ(), max(v1top.getZ(), max(v2top.getZ(), v3top.getZ())))));

        return new BlockBox((int) floor(minX), (int) floor(minY), (int) floor(minZ),
                (int) floor(maxX), (int) floor(maxY), (int) floor(maxZ));
    }

    @Override
    public Vec3d center() {
        double cx = (v1.getX() + v2.getX() + v3.getX()) / 3;
        double cy = (v1.getY() + v2.getY() + v3.getY()) / 3;
        double cz = (v1.getZ() + v2.getZ() + v3.getZ()) / 3;

        return new Vec3d(
                cx + direction.getX() * height / 2,
                cy + direction.getY() * height / 2,
                cz + direction.getZ() * height / 2
        );
    }
}

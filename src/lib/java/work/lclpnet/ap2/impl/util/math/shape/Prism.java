package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.world.phys.Vec3;
import work.lclpnet.gaco.ds.BlockBox;

import static java.lang.Math.*;

public class Prism implements Shape {

    private final Vec3 v1, v2, v3;
    private final double height;
    private final Vec3 direction;

    public Prism(Vec3 v1, Vec3 v2, Vec3 v3, double height, Vec3 direction) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.height = height;
        this.direction = direction;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        Vec3 p = new Vec3(x, y, z);
        Vec3 diff = p.subtract(v1);

        double projHeight = diff.dot(direction);

        if (projHeight < 0 || projHeight > height) {
            return false;
        }

        Vec3 proj = p.subtract(direction.scale(projHeight));

        Vec3 edge1 = v2.subtract(v1);
        Vec3 edge2 = v3.subtract(v1);
        Vec3 pv = proj.subtract(v1);

        double d00 = edge1.dot(edge1);
        double d01 = edge1.dot(edge2);
        double d11 = edge2.dot(edge2);
        double d20 = pv.dot(edge1);
        double d21 = pv.dot(edge2);

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
        Vec3 topOffset = direction.scale(height);
        Vec3 v1top = v1.add(topOffset);
        Vec3 v2top = v2.add(topOffset);
        Vec3 v3top = v3.add(topOffset);

        // Find the min and max coordinates among all 6 vertices
        double minX = min(v1.x(), min(v2.x(), min(v3.x(), min(v1top.x(), min(v2top.x(), v3top.x())))));
        double minY = min(v1.y(), min(v2.y(), min(v3.y(), min(v1top.y(), min(v2top.y(), v3top.y())))));
        double minZ = min(v1.z(), min(v2.z(), min(v3.z(), min(v1top.z(), min(v2top.z(), v3top.z())))));

        double maxX = max(v1.x(), max(v2.x(), max(v3.x(), max(v1top.x(), max(v2top.x(), v3top.x())))));
        double maxY = max(v1.y(), max(v2.y(), max(v3.y(), max(v1top.y(), max(v2top.y(), v3top.y())))));
        double maxZ = max(v1.z(), max(v2.z(), max(v3.z(), max(v1top.z(), max(v2top.z(), v3top.z())))));

        return new BlockBox((int) floor(minX), (int) floor(minY), (int) floor(minZ),
                (int) floor(maxX), (int) floor(maxY), (int) floor(maxZ));
    }

    @Override
    public Vec3 center() {
        double cx = (v1.x() + v2.x() + v3.x()) / 3;
        double cy = (v1.y() + v2.y() + v3.y()) / 3;
        double cz = (v1.z() + v2.z() + v3.z()) / 3;

        return new Vec3(
                cx + direction.x() * height / 2,
                cy + direction.y() * height / 2,
                cz + direction.z() * height / 2
        );
    }
}

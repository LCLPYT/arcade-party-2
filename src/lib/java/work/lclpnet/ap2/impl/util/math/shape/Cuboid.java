package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.gaco.ds.BlockBox;

import static java.lang.Math.*;

public class Cuboid implements Shape {

    private final double minX, minY, minZ, maxX, maxY, maxZ;

    public Cuboid(Vec3d center, double width, double height, double length) {
        this(center.getX() - width / 2, center.getY() - height / 2, center.getZ() - length / 2,
                center.getX() + width / 2, center.getY() + height / 2, center.getZ() + length / 2);
    }

    public Cuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = min(minX, maxX);
        this.minY = min(minY, maxY);
        this.minZ = min(minZ, maxZ);
        this.maxX = max(minX, maxX);
        this.maxY = max(minY, maxY);
        this.maxZ = max(minZ, maxZ);
    }

    @Override
    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    @Override
    public BlockBox bounds() {
        return new BlockBox((int) floor(minX), (int) floor(minY), (int) floor(minZ),
                (int) floor(maxX), (int) floor(maxY), (int) floor(maxZ));
    }

    @Override
    public Vec3d center() {
        return new Vec3d((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
    }
}

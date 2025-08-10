package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.face.Face;

import static java.lang.Math.floor;
import static java.lang.Math.sqrt;

public class Tetrahedron implements PlatonicShape {

    private final Vec3d center;
    private final double radius;
    private final Vec3d[] vertices;
    private final Face[] faces;

    public Tetrahedron(Vec3d center, double radius) {
        this.center = center;
        this.radius = radius;
        this.vertices = PlatonicShape.super.vertices();
        this.faces = PlatonicShape.super.faces();
    }

    @Override
    public Vec3d center() {
        return center;
    }

    @Override
    public double radius() {
        return radius;
    }

    @Override
    public Vec3d[] vertices() {
        return vertices;
    }

    @Override
    public Face[] faces() {
        return faces;
    }

    @Override
    public Vec3d[] unitVertices() {
        double a = 1.0 / 3.0;
        double b = sqrt(8.0 / 9.0);
        double c = sqrt(2.0 / 9.0);
        double d = sqrt(2.0 / 3.0);

        return new Vec3d[]{
                new Vec3d(0, 1, 0),
                new Vec3d(-c, -a, d),
                new Vec3d(-c, -a, -d),
                new Vec3d(b, -a, 0)
        };
    }

    @Override
    public int[][] faceIndices() {
        return new int[][]{
                {0, 2, 1},
                {0, 3, 2},
                {0, 1, 3},
                {3, 1, 2}
        };
    }

    @Override
    public BlockBox bounds() {
        return new BlockBox(
                (int) floor(center.getX() - radius), (int) floor(center.getY() - radius / 3d), (int) floor(center.getZ() - radius),
                (int) floor(center.getX() + radius), (int) floor(center.getY() + radius), (int) floor(center.getZ() + radius)
        );
    }
}

package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.math.face.Face;

import static java.lang.Math.sqrt;

public class Cube extends Cuboid implements PlatonicShape {

    public static final Cube UNIT = new Cube(Vec3d.ZERO, 1.d);

    private final double radius;
    private final Vec3d[] vertices;
    private final Face[] faces;

    public Cube(Vec3d center, double radius) {
        super(center, radius * 2, radius * 2, radius * 2);
        this.radius = radius;
        this.vertices = PlatonicShape.super.vertices();
        this.faces = PlatonicShape.super.faces();
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
        double a = 1.0 / sqrt(3.0);

        return new Vec3d[]{
                new Vec3d(-a, -a, -a),
                new Vec3d(a, -a, -a),
                new Vec3d(a, a, -a),
                new Vec3d(-a, a, -a),
                new Vec3d(-a, -a, a),
                new Vec3d(a, -a, a),
                new Vec3d(a, a, a),
                new Vec3d(-a, a, a)
        };
    }

    @Override
    public int[][] faceIndices() {
        return new int[][]{
                {3, 2, 1, 0},
                {2, 6, 5, 1},
                {5, 6, 7, 4},
                {0, 4, 7, 3},
                {3, 7, 6, 2},
                {1, 5, 4, 0}
        };
    }

    @Override
    public double distance(double x, double y, double z) {
        return Shapes.chebyshevDelta(x, y, z);
    }
}

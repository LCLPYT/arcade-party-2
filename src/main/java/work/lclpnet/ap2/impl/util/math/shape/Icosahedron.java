package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.math.face.Face;

import static work.lclpnet.ap2.impl.util.math.MathUtil.PHI;

public class Icosahedron implements PlatonicShape {

    public static Icosahedron UNIT = new Icosahedron(Vec3d.ZERO, 1.d);

    private final Vec3d center;
    private final double radius;
    private final Vec3d[] vertices;
    private final Face[] faces;

    public Icosahedron(Vec3d center, double radius) {
        this.center = center;
        this.radius = radius;
        this.vertices = PlatonicShape.super.vertices();
        this.faces = PlatonicShape.super.faces();
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
    public Vec3d center() {
        return center;
    }

    @Override
    public double radius() {
        return radius;
    }

    @Override
    public Vec3d[] unitVertices() {
        double a = 1.0;
        double b = 1.0 / PHI;

        return new Vec3d[]{
                new Vec3d(0, b, -a),
                new Vec3d(b, a, 0),
                new Vec3d(-b, a, 0),
                new Vec3d(0, b, a),
                new Vec3d(0, -b, a),
                new Vec3d(-a, 0, b),
                new Vec3d(0, -b, -a),
                new Vec3d(a, 0, -b),
                new Vec3d(a, 0, b),
                new Vec3d(-a, 0, -b),
                new Vec3d(b, -a, 0),
                new Vec3d(-b, -a, 0)
        };
    }

    @Override
    public int[][] faceIndices() {
        return new int[][]{
                {2, 1, 0},
                {1, 2, 3},
                {5, 4, 3},
                {4, 8, 3},
                {7, 6, 0},
                {6, 9, 0},
                {11, 10, 4},
                {10, 11, 6},
                {9, 5, 2},
                {5, 9, 11},
                {8, 7, 1},
                {7, 8, 10},
                {2, 5, 3},
                {8, 1, 3},
                {9, 2, 0},
                {1, 7, 0},
                {11, 9, 6},
                {7, 10, 6},
                {5, 11, 4},
                {10, 8, 4},
        };
    }
}

package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.math.face.Face;

import static java.lang.Math.abs;

public class Octahedron implements PlatonicShape {

    private final Vec3d center;
    private final double radius;
    private final Vec3d[] vertices;
    private final Face[] faces;

    public Octahedron(Vec3d center, double radius) {
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
    public boolean contains(double x, double y, double z) {
        return abs(x - center.getX()) + abs(y - center.getY()) + abs(z - center.getZ()) < radius;
    }

    @Override
    public Vec3d[] unitVertices() {
        return normalize(dualVertices(Cube.UNIT));
    }

    @Override
    public int[][] faceIndices() {
        return dualFaceIndices(Cube.UNIT);
    }
}

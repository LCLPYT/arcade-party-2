package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.math.face.Face;

public class Dodecahedron implements PlatonicShape {

    private final Vec3d center;
    private final double radius;
    private final Vec3d[] vertices;
    private final Face[] faces;

    public Dodecahedron(Vec3d center, double radius) {
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
        return normalize(dualVertices(Icosahedron.UNIT));
    }

    @Override
    public int[][] faceIndices() {
        return dualFaceIndices(Icosahedron.UNIT);
    }
}

package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.impl.util.math.face.Face;
import work.lclpnet.gaco.ds.BlockBox;

import static java.lang.Math.floor;
import static java.lang.Math.sqrt;

public class Tetrahedron implements PlatonicShape {

    private final Vec3 center;
    private final double radius;
    private final Vec3[] vertices;
    private final Face[] faces;

    public Tetrahedron(Vec3 center, double radius) {
        this.center = center;
        this.radius = radius;
        this.vertices = PlatonicShape.super.vertices();
        this.faces = PlatonicShape.super.faces();
    }

    @Override
    public Vec3 center() {
        return center;
    }

    @Override
    public double radius() {
        return radius;
    }

    @Override
    public Vec3[] vertices() {
        return vertices;
    }

    @Override
    public Face[] faces() {
        return faces;
    }

    @Override
    public Vec3[] unitVertices() {
        double a = 1.0 / 3.0;
        double b = sqrt(8.0 / 9.0);
        double c = sqrt(2.0 / 9.0);
        double d = sqrt(2.0 / 3.0);

        return new Vec3[]{
                new Vec3(0, 1, 0),
                new Vec3(-c, -a, d),
                new Vec3(-c, -a, -d),
                new Vec3(b, -a, 0)
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
                (int) floor(center.x() - radius), (int) floor(center.y() - radius / 3d), (int) floor(center.z() - radius),
                (int) floor(center.x() + radius), (int) floor(center.y() + radius), (int) floor(center.z() + radius)
        );
    }
}

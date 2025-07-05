package work.lclpnet.ap2.impl.util.math.face;

import net.minecraft.util.math.Vec3d;

public interface Face {

    Vec3d[] vertices();

    default Vec3d normal() {
        Vec3d[] vertices = vertices();

        if (vertices.length < 3) throw new UnsupportedOperationException("Three vertices are required");

        return vertices[1].subtract(vertices[0]).crossProduct(vertices[2].subtract(vertices[0])).normalize();
    }

    default Vec3d center() {
        Vec3d[] vertices = vertices();

        double x = 0, y = 0, z = 0;

        for (Vec3d vertex : vertices) {
            x += vertex.x;
            y += vertex.y;
            z += vertex.z;
        }

        return new Vec3d(x / vertices.length, y / vertices.length, z / vertices.length);
    }
}

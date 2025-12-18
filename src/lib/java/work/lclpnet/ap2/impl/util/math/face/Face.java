package work.lclpnet.ap2.impl.util.math.face;

import net.minecraft.world.phys.Vec3;

public interface Face {

    Vec3[] vertices();

    default Vec3 normal() {
        Vec3[] vertices = vertices();

        if (vertices.length < 3) throw new UnsupportedOperationException("Three vertices are required");

        return vertices[1].subtract(vertices[0]).cross(vertices[2].subtract(vertices[0])).normalize();
    }

    default Vec3 center() {
        Vec3[] vertices = vertices();

        double x = 0, y = 0, z = 0;

        for (Vec3 vertex : vertices) {
            x += vertex.x;
            y += vertex.y;
            z += vertex.z;
        }

        return new Vec3(x / vertices.length, y / vertices.length, z / vertices.length);
    }
}

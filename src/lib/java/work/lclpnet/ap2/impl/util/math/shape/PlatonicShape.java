package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.util.math.Vec3d;

public interface PlatonicShape extends Polyhedron, SphereBoundedShape {

    Vec3d[] unitVertices();

    @Override
    default Vec3d[] vertices() {
        Vec3d[] vertices = unitVertices();
        Vec3d center = center();
        double radius = radius();

        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = vertices[i].multiply(radius).add(center);
        }

        return vertices;
    }
}

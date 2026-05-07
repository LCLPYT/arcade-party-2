package work.lclpnet.ap2.impl.util.math.shape;

import net.minecraft.world.phys.Vec3;

public interface PlatonicShape extends Polyhedron, SphereBoundedShape {

    Vec3[] unitVertices();

    @Override
    default Vec3[] vertices() {
        Vec3[] vertices = unitVertices();
        Vec3 center = center();
        double radius = radius();

        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = vertices[i].scale(radius).add(center);
        }

        return vertices;
    }
}

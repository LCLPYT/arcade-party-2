package work.lclpnet.ap2.impl.util.math.shape;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.debug.DebugRenderer;
import work.lclpnet.ap2.impl.util.math.face.Face;
import work.lclpnet.ap2.impl.util.math.face.Polygon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.atan2;

public interface Polyhedron extends Shape {

    Vec3d[] vertices();

    int[][] faceIndices();

    default Face[] faces() {
        Vec3d[] vertices = vertices();
        int[][] faceIndices = faceIndices();

        Polygon[] faces = new Polygon[faceIndices.length];

        for (int i = 0; i < faceIndices.length; i++) {
            int[] indices = faceIndices[i];
            Vec3d[] face = new Vec3d[indices.length];

            for (int j = 0; j < indices.length; j++) {
                face[j] = vertices[indices[j]];
            }

            faces[i] = new Polygon(face);
        }

        return faces;
    }

    @Override
    default boolean contains(double x, double y, double z) {
        Vec3d point = new Vec3d(x, y, z);

        for (Face face : faces()) {
            Vec3d dir = point.subtract(face.vertices()[0]);

            if (face.normal().dotProduct(dir) > 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    default void debug(DebugController controller) {
        DebugRenderer renderer = controller.renderer().orElse(null);

        if (renderer == null) return;

        for (Vec3d vertex : vertices()) {
            renderer.marker(vertex, Blocks.RED_CONCRETE.getDefaultState(), 0xff0000);
        }

        for (Face face : faces()) {
            renderer.arrow(face.center(), face.normal(), Blocks.ORANGE_TERRACOTTA.getDefaultState());

            Vec3d[] vertices = face.vertices();

            for (int i = 0; i < vertices.length; i++) {
                renderer.line(vertices[i], vertices[(i + 1) % vertices.length], 0.1, Blocks.YELLOW_CONCRETE.getDefaultState());
            }
        }

        renderer.box(bounds(), Blocks.RED_STAINED_GLASS.getDefaultState());
    }

    default Vec3d[] normalize(Vec3d[] vertices) {
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = vertices[i].normalize();
        }

        return vertices;
    }

    default Vec3d[] dualVertices(Polyhedron mesh) {
        Face[] faces = mesh.faces();
        Vec3d[] vertices = new Vec3d[faces.length];

        for (int f = 0; f < faces.length; f++) {
            vertices[f] = faces[f].center();
        }

        return vertices;
    }

    default int[][] dualFaceIndices(Polyhedron mesh) {
        Vec3d[] vertices = mesh.vertices();
        int[][] faceIndices = mesh.faceIndices();
        Vec3d[] faceCenters = Arrays.stream(mesh.faces()).map(Face::center).toArray(Vec3d[]::new);

        IntList adj = new IntArrayList();
        List<IntList> dualFaces = new ArrayList<>();

        for (int v = 0; v < vertices.length; v++) {
            // for each vertex, build a new polygon face with the center vertices of all adjacent faces
            collectAdjacentFaceIndices(faceIndices, v, adj);

            Vec3d vertex = vertices[v];
            Vec3d refDir = faceCenters[adj.getInt(0)].subtract(vertex).normalize();
            Vec3d perpDir = vertex.normalize().crossProduct(refDir).normalize();

            adj.sort((f1, f2) -> {
                Vec3d d1 = faceCenters[f1].subtract(vertex);
                Vec3d d2 = faceCenters[f2].subtract(vertex);

                double a1 = atan2(perpDir.dotProduct(d1), refDir.dotProduct(d1));
                double a2 = atan2(perpDir.dotProduct(d2), refDir.dotProduct(d2));

                return Double.compare(a1, a2);
            });

            dualFaces.add(new IntArrayList(adj));
        }

        return dualFaces.stream().map(IntList::toIntArray).toArray(int[][]::new);
    }

    private void collectAdjacentFaceIndices(int[][] faceIndices, int vertexIndex, IntList connectedFaceIndices) {
        connectedFaceIndices.clear();

        for (int f = 0; f < faceIndices.length; f++) {
            int[] vertexIndices = faceIndices[f];

            if (arrayContains(vertexIndices, vertexIndex)) {
                connectedFaceIndices.add(f);
            }
        }
    }

    private boolean arrayContains(int[] array, int elem) {
        for (int item : array) {
            if (elem == item) {
                return true;
            }
        }

        return false;
    }
}

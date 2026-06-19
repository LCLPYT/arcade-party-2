package work.lclpnet.ap2.impl.util.math.shape;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.debug.DebugRenderer;
import work.lclpnet.ap2.impl.util.math.face.Face;
import work.lclpnet.ap2.impl.util.math.face.Polygon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.atan2;

public interface Polyhedron extends Shape {

    Vec3[] vertices();

    int[][] faceIndices();

    default Face[] faces() {
        Vec3[] vertices = vertices();
        int[][] faceIndices = faceIndices();

        Polygon[] faces = new Polygon[faceIndices.length];

        for (int i = 0; i < faceIndices.length; i++) {
            int[] indices = faceIndices[i];
            Vec3[] face = new Vec3[indices.length];

            for (int j = 0; j < indices.length; j++) {
                face[j] = vertices[indices[j]];
            }

            faces[i] = new Polygon(face);
        }

        return faces;
    }

    @Override
    default boolean contains(double x, double y, double z) {
        Vec3 point = new Vec3(x, y, z);

        for (Face face : faces()) {
            Vec3 dir = point.subtract(face.vertices()[0]);

            if (face.normal().dot(dir) > 0) {
                return false;
            }
        }

        return true;
    }

    @Override
    default void debug(DebugController controller) {
        DebugRenderer renderer = controller.renderer().orElse(null);

        if (renderer == null) return;

        for (Vec3 vertex : vertices()) {
            renderer.marker(vertex, Blocks.CONCRETE.red().defaultBlockState(), 0xff0000);
        }

        for (Face face : faces()) {
            renderer.arrow(face.center(), face.normal(), Blocks.DYED_TERRACOTTA.orange().defaultBlockState());

            Vec3[] vertices = face.vertices();

            for (int i = 0; i < vertices.length; i++) {
                renderer.line(vertices[i], vertices[(i + 1) % vertices.length], 0.1, Blocks.CONCRETE.yellow().defaultBlockState());
            }
        }

        renderer.box(bounds(), Blocks.STAINED_GLASS.red().defaultBlockState());
    }

    default Vec3[] normalize(Vec3[] vertices) {
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = vertices[i].normalize();
        }

        return vertices;
    }

    default Vec3[] dualVertices(Polyhedron mesh) {
        Face[] faces = mesh.faces();
        Vec3[] vertices = new Vec3[faces.length];

        for (int f = 0; f < faces.length; f++) {
            vertices[f] = faces[f].center();
        }

        return vertices;
    }

    default int[][] dualFaceIndices(Polyhedron mesh) {
        Vec3[] vertices = mesh.vertices();
        int[][] faceIndices = mesh.faceIndices();
        Vec3[] faceCenters = Arrays.stream(mesh.faces()).map(Face::center).toArray(Vec3[]::new);

        IntList adj = new IntArrayList();
        List<IntList> dualFaces = new ArrayList<>();

        for (int v = 0; v < vertices.length; v++) {
            // for each vertex, build a new polygon face with the center vertices of all adjacent faces
            collectAdjacentFaceIndices(faceIndices, v, adj);

            Vec3 vertex = vertices[v];
            Vec3 refDir = faceCenters[adj.getInt(0)].subtract(vertex).normalize();
            Vec3 perpDir = vertex.normalize().cross(refDir).normalize();

            adj.sort((f1, f2) -> {
                Vec3 d1 = faceCenters[f1].subtract(vertex);
                Vec3 d2 = faceCenters[f2].subtract(vertex);

                double a1 = atan2(perpDir.dot(d1), refDir.dot(d1));
                double a2 = atan2(perpDir.dot(d2), refDir.dot(d2));

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

package work.lclpnet.ap2.game.guess_it.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.debug.DebugRenderer;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static java.lang.Math.*;
import static java.lang.Math.atan2;
import static work.lclpnet.ap2.impl.util.math.MathUtil.PHI;

public class Shapes {

    public static <S extends BlockShape & BlockShape.WithHeight & BlockShape.WithRadius> @NotNull Shape getRandomShape(
            Random random, S stage
    ) {
        final int maxRadius = min(stage.height() / 2, stage.radius());
        final int maxSquareRadius = (int) floor(sin(Math.PI * 0.25) * stage.radius());
        final Vec3d center = stage.center().toCenterPos();
        final Vec3d origin = stage.origin().toCenterPos();

        List<Supplier<Shape>> shapes = List.of(
                () -> {
                    final int minRadius = 4;

                    int width = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));
                    int height = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));
                    int length = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));

                    return new Cuboid(origin.add(0, height * 0.5d, 0), width, height, length);
                },
                () -> {
                    final int minRadius = 4;

                    int radius = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));

                    return new Cube(origin.add(0, radius, 0), radius);
                },
                () -> {
                    final int minRadius = 4;

                    int a = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
                    int b = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
                    int c = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

                    return new Ellipsoid(center, a, b, c);
                },
                () -> {
                    final int minRadius = 4;

                    int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

                    return new Sphere(center, radius);
                },
                () -> {
                    final int minRadius = 4;
                    final int minHeight = 10;
                    final int maxHeight = stage.height();

                    int radius = minRadius + random.nextInt(max(1, maxRadius - minRadius));
                    int height = minHeight + random.nextInt(max(1, maxHeight - minHeight));

                    return new Cone(origin, radius, height);
                },
                () -> {
                    final int minRadius = 4;
                    final int minHeight = 8;
                    final int maxHeight = stage.height();

                    int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
                    int height = minHeight + random.nextInt(max(1, maxHeight - minHeight));

                    return new Cylinder(origin, radius, height);
                },
                () -> {
                    final int minRadius = 4;

                    int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
                    Vec3d normal = MathUtil.randomUnitVec3d(random);

                    return new Hemisphere(center, radius, normal);
                },
                () -> {
                    final int minRadius = 4;

                    int radius = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));

                    return new Pyramid(origin, radius, radius - 0.5);
                },
                () -> {
                    final int minRadius = 5;
                    final int minHeight = 8;
                    final int maxHeight = stage.height();
                    final double minAngle = toRadians(20);

                    int r1 = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
                    int r2 = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
                    int r3 = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

                    int height = minHeight + random.nextInt(max(1, maxHeight - minHeight));

                    double alpha = random.nextDouble() * PI;
                    double beta = alpha + minAngle + random.nextDouble() * (PI - 2 * minAngle);
                    double gamma = (alpha + beta) / 2 + PI;

                    Vec3d v1 = origin.add(sin(alpha) * r1, 0, cos(alpha) * r1);
                    Vec3d v2 = origin.add(sin(beta)  * r2, 0, cos(beta)  * r2);
                    Vec3d v3 = origin.add(sin(gamma) * r3, 0, cos(gamma) * r3);

                    return new Prism(v1, v2, v3, height, new Vec3d(0, 1, 0));
                },
                () -> {
                    final int minMinorRadius = 2;
                    final int maxMinorRadius = 5;

                    int minorRadius = minMinorRadius + random.nextInt(maxMinorRadius - minMinorRadius + 1);

                    final int maxMajorRadius = maxRadius - minorRadius;
                    final int minMajorRadius = minorRadius + 3;

                    int majorRadius = minMajorRadius + random.nextInt(maxMajorRadius - minMajorRadius + 1);

                    return new Torus(center, majorRadius, minorRadius);
                },
                () -> {
                    final int minRadius = 6;

                    int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

                    return new Tetrahedron(origin.add(0, radius / 3d, 0), radius);
                },
                () -> {
                    final int minRadius = 4;

                    int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

                    return new Octahedron(center, radius);
                },
                () -> {
                    final int minRadius = 4;

                    int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

                    return new Icosahedron(center, radius);
                },
                () -> {
                    final int minRadius = 4;

                    int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

                    return new Dodecahedron(center, radius);
                }
        );


        return shapes.get(random.nextInt(shapes.size())).get();
    }

    private static double chebyshevDist(double dx, double dy, double dz) {
        return max(abs(dx), max(abs(dy), abs(dz)));
    }

    private static double euclideanDist(double dx, double dy, double dz) {
        return sqrt(dx * dx + dy * dy + dz * dz);
    }

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

    public interface SphereBoundedShape extends Shape {

        Vec3d center();

        double radius();

        @Override
        default BlockBox bounds() {
            Vec3d center = center();
            double radius = radius();

            return new BlockBox(
                    (int) floor(center.getX() - radius), (int) floor(center.getY() - radius), (int) floor(center.getZ() - radius),
                    (int) floor(center.getX() + radius), (int) floor(center.getY() + radius), (int) floor(center.getZ() + radius)
            );
        }

        @Override
        default double distance(double x, double y, double z) {
            return euclideanDist(x, y, z);
        }
    }

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

    public interface Shape {

        boolean contains(double x, double y, double z);

        /**
         * @return An axis-aligned bounding box containing the whole shape.
         */
        BlockBox bounds();

        /**
         * @param x The x position.
         * @param y The y position.
         * @param z The z position.
         * @return The double distance towards the shape center.
         */
        default double distance(double x, double y, double z) {
            return chebyshevDist(x, y, z);
        }

        default void debug(DebugController controller) {}
    }

    public static class Cuboid implements Shape {

        private final double minX, minY, minZ, maxX, maxY, maxZ;

        public Cuboid(Vec3d center, double width, double height, double length) {
            this(center.getX() - width / 2, center.getY() - height / 2, center.getZ() - length / 2,
                    center.getX() + width / 2, center.getY() + height / 2, center.getZ() + length / 2);
        }

        public Cuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = min(minX, maxX);
            this.minY = min(minY, maxY);
            this.minZ = min(minZ, maxZ);
            this.maxX = max(minX, maxX);
            this.maxY = max(minY, maxY);
            this.maxZ = max(minZ, maxZ);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        @Override
        public BlockBox bounds() {
            return new BlockBox((int) floor(minX), (int) floor(minY), (int) floor(minZ),
                    (int) floor(maxX), (int) floor(maxY), (int) floor(maxZ));
        }

        public Vec3d center() {
            return new Vec3d((minX + maxX) * 0.5,  (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        }
    }

    public static class Cube extends Cuboid implements PlatonicShape {

        public static final Cube UNIT = new Cube(Vec3d.ZERO, 1.d);

        private final double radius;
        private final Vec3d[] vertices;
        private final Face[] faces;

        public Cube(Vec3d center, double radius) {
            super(center, radius * 2, radius * 2, radius * 2);
            this.radius = radius;
            this.vertices = PlatonicShape.super.vertices();
            this.faces = PlatonicShape.super.faces();
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
            double a = 1.0 / sqrt(3.0);

            return new Vec3d[] {
                    new Vec3d(-a, -a, -a),
                    new Vec3d(a, -a, -a),
                    new Vec3d(a, a, -a),
                    new Vec3d(-a, a, -a),
                    new Vec3d(-a, -a, a),
                    new Vec3d(a, -a, a),
                    new Vec3d(a, a, a),
                    new Vec3d(-a, a, a)
            };
        }

        @Override
        public int[][] faceIndices() {
            return new int[][] {
                    {3, 2, 1, 0},
                    {2, 6, 5, 1},
                    {5, 6, 7, 4},
                    {0, 4, 7, 3},
                    {3, 7, 6, 2},
                    {1, 5, 4, 0}
            };
        }
    }

    public static class Ellipsoid implements Shape {

        private final Vec3d center;
        private final double a, b, c;

        public Ellipsoid(Vec3d center, double a, double b, double c) {
            this.center = center;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double dx = x - center.getX();
            double dy = y - center.getY();
            double dz = z - center.getZ();

            return (dx * dx) / (a * a) + (dy * dy) / (b * b) + (dz * dz) / (c * c) < 1.0;
        }

        @Override
        public BlockBox bounds() {
            return new BlockBox(
                    (int) floor(center.getX() - a), (int) floor(center.getY() - b), (int) floor(center.getZ() - c),
                    (int) floor(center.getX() + a), (int) floor(center.getY() + b), (int) floor(center.getZ() + c));
        }

        @Override
        public double distance(double x, double y, double z) {
            return euclideanDist(x, y, z);
        }

        public Vec3d center() {
            return center;
        }
    }

    public static class Sphere extends Ellipsoid implements SphereBoundedShape {

        private final double radius;

        public Sphere(Vec3d center, double radius) {
            super(center, radius, radius, radius);
            this.radius = radius;
        }

        @Override
        public double radius() {
            return radius;
        }
    }

    public static class Cone implements Shape {

        private final Vec3d origin;
        private final double radius, height;

        public Cone(Vec3d origin, double radius, double height) {
            this.origin = origin;
            this.radius = radius;
            this.height = height;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            if (y < origin.getY() || y > origin.getY() + height) {
                return false;
            }

            double yFraction = (y - origin.getY()) / height;
            double currentRadius = radius * (1.0 - yFraction);

            double dx = x - origin.getX();
            double dz = z - origin.getZ();

            return dx * dx + dz * dz < currentRadius * currentRadius;
        }

        @Override
        public BlockBox bounds() {
            return new BlockBox(
                    (int) floor(origin.getX() - radius), (int) floor(origin.getY()), (int) floor(origin.getZ() - radius),
                    (int) floor(origin.getX() + radius), (int) floor(origin.getY() + height), (int) floor(origin.getZ() + radius));
        }
    }

    public static class Cylinder implements Shape {

        private final Vec3d origin;
        private final double radius, height;

        public Cylinder(Vec3d origin, double radius, double height) {
            this.origin = origin;
            this.radius = radius;
            this.height = height;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            if (y < origin.getY() || y > origin.getY() + height) {
                return false;
            }

            double dx = x - origin.getX();
            double dz = z - origin.getZ();

            return dx * dx + dz * dz < radius * radius;
        }

        @Override
        public BlockBox bounds() {
            return new BlockBox(
                    (int) floor(origin.getX() - radius), (int) floor(origin.getY()), (int) floor(origin.getZ() - radius),
                    (int) floor(origin.getX() + radius), (int) floor(origin.getY() + height), (int) floor(origin.getZ() + radius));
        }
    }

    public static class Hemisphere implements SphereBoundedShape {

        private final Vec3d center;
        private final double radius;
        private final Vec3d normal;

        public Hemisphere(Vec3d center, double radius, Vec3d normal) {
            this.center = center;
            this.radius = radius;
            this.normal = normal;
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
        public boolean contains(double x, double y, double z) {
            double dx = x - center.getX();
            double dy = y - center.getY();
            double dz = z - center.getZ();

            boolean inSphere = (dx * dx + dy * dy + dz * dz) < radius * radius;

            if (!inSphere) {
                return false;
            }

            return (dx * normal.getX() + dy * normal.getY() + dz * normal.getZ()) >= 0;
        }
    }

    public static class Pyramid implements Shape {

        private final Vec3d origin;
        private final double radius;
        private final double height;

        public Pyramid(Vec3d origin, double radius, double height) {
            this.origin = origin;
            this.radius = radius;
            this.height = height;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            if (y < origin.getY() || y > origin.getY() + height) {
                return false;
            }

            double yFraction = (y - origin.getY()) / height;
            double currentRadius = radius * (1.0 - yFraction);

            return abs(x - origin.getX()) < currentRadius && abs(z - origin.getZ()) < currentRadius;
        }

        @Override
        public BlockBox bounds() {
            return new BlockBox(
                    (int) floor(origin.getX() - radius), (int) floor(origin.getY()), (int) floor(origin.getZ() - radius),
                    (int) floor(origin.getX() + radius), (int) floor(origin.getY() + height), (int) floor(origin.getZ() + radius));
        }
    }

    public static class Prism implements Shape {

        private final Vec3d v1, v2, v3;
        private final double height;
        private final Vec3d direction;

        public Prism(Vec3d v1, Vec3d v2, Vec3d v3, double height, Vec3d direction) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.height = height;
            this.direction = direction;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            Vec3d p = new Vec3d(x, y, z);
            Vec3d diff = p.subtract(v1);

            double projHeight = diff.dotProduct(direction);

            if (projHeight < 0 || projHeight > height) {
                return false;
            }

            Vec3d proj = p.subtract(direction.multiply(projHeight));

            Vec3d edge1 = v2.subtract(v1);
            Vec3d edge2 = v3.subtract(v1);
            Vec3d pv = proj.subtract(v1);

            double d00 = edge1.dotProduct(edge1);
            double d01 = edge1.dotProduct(edge2);
            double d11 = edge2.dotProduct(edge2);
            double d20 = pv.dotProduct(edge1);
            double d21 = pv.dotProduct(edge2);

            // barycentric coordinates
            double d = d00 * d11 - d01 * d01;

            if (abs(d) < 1e-9) {
                return false;
            }

            double u = (d11 * d20 - d01 * d21) / d;
            double v = (d00 * d21 - d01 * d20) / d;

            return (u >= 0) && (v >= 0) && (u + v <= 1);
        }

        @Override
        public BlockBox bounds() {
            Vec3d topOffset = direction.multiply(height);
            Vec3d v1top = v1.add(topOffset);
            Vec3d v2top = v2.add(topOffset);
            Vec3d v3top = v3.add(topOffset);

            // Find the min and max coordinates among all 6 vertices
            double minX = min(v1.getX(), min(v2.getX(), min(v3.getX(), min(v1top.getX(), min(v2top.getX(), v3top.getX())))));
            double minY = min(v1.getY(), min(v2.getY(), min(v3.getY(), min(v1top.getY(), min(v2top.getY(), v3top.getY())))));
            double minZ = min(v1.getZ(), min(v2.getZ(), min(v3.getZ(), min(v1top.getZ(), min(v2top.getZ(), v3top.getZ())))));

            double maxX = max(v1.getX(), max(v2.getX(), max(v3.getX(), max(v1top.getX(), max(v2top.getX(), v3top.getX())))));
            double maxY = max(v1.getY(), max(v2.getY(), max(v3.getY(), max(v1top.getY(), max(v2top.getY(), v3top.getY())))));
            double maxZ = max(v1.getZ(), max(v2.getZ(), max(v3.getZ(), max(v1top.getZ(), max(v2top.getZ(), v3top.getZ())))));

            return new BlockBox((int) floor(minX), (int) floor(minY), (int) floor(minZ),
                    (int) floor(maxX), (int) floor(maxY), (int) floor(maxZ));
        }
    }

    public static class Torus implements Shape {

        private final Vec3d center;
        private final double majorRadius;
        private final double minorRadius;

        public Torus(Vec3d center, double majorRadius, double minorRadius) {
            this.center = center;
            this.majorRadius = majorRadius;
            this.minorRadius = minorRadius;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double dx = x - center.getX();
            double dy = y - center.getY();
            double dz = z - center.getZ();

            double term = majorRadius - sqrt(dx * dx + dz * dz);

            return term * term + dy * dy < minorRadius * minorRadius;
        }

        @Override
        public BlockBox bounds() {
            double totalRadius = majorRadius + minorRadius;

            return new BlockBox(
                    (int) floor(center.getX() - totalRadius), (int) floor(center.getY() - minorRadius), (int) floor(center.getZ() - totalRadius),
                    (int) floor(center.getX() + totalRadius), (int) floor(center.getY() + minorRadius), (int) floor(center.getZ() + totalRadius));
        }

        @Override
        public double distance(double x, double y, double z) {
            double qx = sqrt(x * x + z * z);
            double ringDist = abs(qx - 10);

            return sqrt(ringDist * ringDist + y * y);
        }
    }

    public static class Tetrahedron implements PlatonicShape {

        private final Vec3d center;
        private final double radius;
        private final Vec3d[] vertices;
        private final Face[] faces;

        public Tetrahedron(Vec3d center, double radius) {
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
            double a = 1.0 / 3.0;
            double b = sqrt(8.0 / 9.0);
            double c = sqrt(2.0 / 9.0);
            double d = sqrt(2.0 / 3.0);

            return new Vec3d[] {
                    new Vec3d(0, 1, 0),
                    new Vec3d(-c, -a, d),
                    new Vec3d(-c, -a, -d),
                    new Vec3d(b, -a, 0)
            };
        }

        @Override
        public int[][] faceIndices() {
            return new int[][] {
                    {0, 2, 1},
                    {0, 3, 2},
                    {0, 1, 3},
                    {3, 1, 2}
            };
        }

        @Override
        public BlockBox bounds() {
            return new BlockBox(
                    (int) floor(center.getX() - radius), (int) floor(center.getY() - radius / 3d), (int) floor(center.getZ() - radius),
                    (int) floor(center.getX() + radius), (int) floor(center.getY() + radius), (int) floor(center.getZ() + radius)
            );
        }
    }

    public static class Octahedron implements PlatonicShape {

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

    public static class Icosahedron implements PlatonicShape {

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

            return new Vec3d[] {
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
            return new int[][] {
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

    public static class Dodecahedron implements PlatonicShape {

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

    public record Polygon(Vec3d[] vertices) implements Face {}
}

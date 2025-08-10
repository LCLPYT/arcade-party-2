package work.lclpnet.ap2.game.guess_it.util;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.math.shape.*;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;

import java.util.*;

import static java.lang.Math.*;

public class BlockCountShapeManager<S extends BlockShape & BlockShape.WithHeight & BlockShape.WithRadius> {

    private final Map<String, ShapeProvider> shapesById = new HashMap<>();
    private final List<ShapeProvider> shapes = new ArrayList<>();
    private final Random random;
    private final S stage;

    public BlockCountShapeManager(Random random, S stage) {
        this.random = random;
        this.stage = stage;

        registerShapes();
    }

    private void registerShapes() {
        final int maxRadius = min(stage.height() / 2, stage.radius());
        final int maxSquareRadius = (int) floor(sin(PI * 0.25) * stage.radius());
        final Vec3d center = stage.center().toCenterPos();
        final Vec3d origin = stage.origin().toCenterPos();

        register("cuboid", () -> {
            final int minRadius = 4;

            int width = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));
            int height = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));
            int length = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));

            return new Cuboid(origin.add(0, height * 0.5d, 0), width, height, length);
        });

        register("cube", () -> {
            final int minRadius = 4;

            int radius = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));

            return new Cube(origin.add(0, radius, 0), radius);
        });

        register("ellipsoid", () -> {
            final int minRadius = 4;

            int a = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
            int b = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
            int c = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

            return new Ellipsoid(center, a, b, c);
        });

        register("sphere", () -> {
            final int minRadius = 4;

            int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

            return new Sphere(center, radius);
        });

        register("cone", () -> {
            final int minRadius = 4;
            final int minHeight = 10;
            final int maxHeight = stage.height();

            int radius = minRadius + random.nextInt(max(1, maxRadius - minRadius));
            int height = minHeight + random.nextInt(max(1, maxHeight - minHeight));

            return new Cone(origin, radius, height);
        });

        register("cylinder", () -> {
            final int minRadius = 4;
            final int minHeight = 8;
            final int maxHeight = stage.height();

            int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
            int height = minHeight + random.nextInt(max(1, maxHeight - minHeight));

            return new Cylinder(origin, radius, height);
        });

        register("hemisphere", () -> {
            final int minRadius = 4;

            int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));
            Vec3d normal = MathUtil.randomUnitVec3d(random);

            return new Hemisphere(center, radius, normal);
        });

        register("pyramid", () -> {
            final int minRadius = 4;

            int radius = minRadius + random.nextInt(max(1, maxSquareRadius - minRadius));

            return new Pyramid(origin, radius, radius - 0.5);
        });

        register("prism", () -> {
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
        });

        register("torus", () -> {
            final int minMinorRadius = 2;
            final int maxMinorRadius = 5;

            int minorRadius = minMinorRadius + random.nextInt(maxMinorRadius - minMinorRadius + 1);

            final int maxMajorRadius = maxRadius - minorRadius;
            final int minMajorRadius = minorRadius + 3;

            int majorRadius = minMajorRadius + random.nextInt(maxMajorRadius - minMajorRadius + 1);

            double maxTilt = PI / 5;

            Quaterniond rotation = new Quaterniond()
                    .rotateZ(random.nextDouble() * 2 * maxTilt - maxTilt)
                    .rotateY(random.nextDouble() * PI)
                    .rotateX(PI / 2);

            return new Torus(center, majorRadius, minorRadius, rotation);
        });

        register("tetrahedron", () -> {
            final int minRadius = 6;

            int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

            return new Tetrahedron(origin.add(0, radius / 3d, 0), radius);
        });

        register("octahedron", () -> {
            final int minRadius = 4;

            int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

            return new Octahedron(center, radius);
        });

        register("icosahedron", () -> {
            final int minRadius = 4;

            int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

            return new Icosahedron(center, radius);
        });

        register("dodecahedron", () -> {
            final int minRadius = 4;

            int radius = min(maxRadius, minRadius + random.nextInt(max(1, stage.radius() - minRadius)));

            return new Dodecahedron(center, radius);
        });
    }

    private void register(String id, ShapeProvider provider) {
        if (shapesById.containsKey(id)) {
            throw new IllegalStateException("Duplicate shape id \"%s\"".formatted(id));
        }

        shapesById.put(id, provider);
        shapes.add(provider);
    }

    public @NotNull Shape getRandomShape() {
        // TODO use restorable queue
        return shapes.get(random.nextInt(shapes.size())).provide();
    }

    public @Nullable Shape getShape(String id) {
        ShapeProvider shape = shapesById.get(id);

        return shape == null ? null : shape.provide();
    }

    public Set<String> getShapes() {
        return Set.copyOf(shapesById.keySet());
    }

    public double distance(Shape shape, double x, double y, double z) {
        DistanceFunction distanceFunction = distanceFunction(shape);
        Vec3d center = shape.center();

        return distanceFunction.distanceTo(x - center.getX(), y - center.getY(), z - center.getZ());
    }

    public DistanceFunction distanceFunction(Shape shape) {
        if (shape instanceof Cube || shape instanceof Tetrahedron) {
            return this::chebyshevDist;
        }

        if (shape instanceof SphereBoundedShape || shape instanceof Ellipsoid) {
            return this::euclideanDist;
        }

        if (shape instanceof Torus t) {
            return (x, y, z) -> {
                Vector3d localPoint = new Vector3d(x, y, z);

                t.rotation().transformInverse(localPoint);

                double qx = sqrt(localPoint.x * localPoint.x + localPoint.z * localPoint.z);
                double ringDist = Math.abs(qx - t.majorRadius());

                return sqrt(ringDist * ringDist + localPoint.y * localPoint.y);
            };
        }

        if (shape instanceof Pyramid p) {
            return (x, y, z) -> (y + p.center().getY()) - p.origin().getY();
        }

        return this::chebyshevDist;
    }

    private double chebyshevDist(double x, double y, double z) {
        return max(abs(x), max(abs(y), abs(z)));
    }

    private double euclideanDist(double x, double y, double z) {
        return sqrt(x * x + y * y + z * z);
    }

    public interface DistanceFunction {
        double distanceTo(double x, double y, double z);
    }

    private interface ShapeProvider {
        Shape provide();
    }
}

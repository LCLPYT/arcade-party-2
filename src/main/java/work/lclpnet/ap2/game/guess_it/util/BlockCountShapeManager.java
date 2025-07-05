package work.lclpnet.ap2.game.guess_it.util;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.math.shape.*;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static java.lang.Math.*;

public class BlockCountShapeManager<S extends BlockShape & BlockShape.WithHeight & BlockShape.WithRadius> {

    private final Random random;
    private final S stage;

    public BlockCountShapeManager(Random random, S stage) {
        this.random = random;
        this.stage = stage;
    }

    public @NotNull Shape getRandomShape() {
        // TODO use restorable queue
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
}

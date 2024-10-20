package work.lclpnet.ap2.impl.util.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.collision.BoxCollisionDetector;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.math.Vec2i;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;

import static java.lang.Math.*;

public class CircleStructureGenerator {

    public static void placeStructures(List<BlockStructure> structures, ServerWorld world, double spacing, PositionFunction position) {
        int minRadius = computeMinimumRadius(structures, spacing);

        // offsets are relative to the origin
        Vec2i[] offsets = CircleStructureGenerator.generateHorizontalOffsets(structures, minRadius);

        placeStructures(structures, world, offsets, position);
    }

    public static void placeStructures(List<BlockStructure> structures, ServerWorld world, Vec2i[] offsets, PositionFunction position) {
        int count = structures.size();

        for (int i = 0; i < count; i++) {
            BlockStructure structure = structures.get(i);
            Vec2i offset = offsets[i];
            BlockPos pos = position.compute(i, structure, offset);

            StructureUtil.placeStructureFast(structure, world, pos);
        }
    }

    /**
     * Compute the minimum radius required of a circle composed of the given structures so that two adjacent
     * structures have a given spacing between them.
     * This also considers the dimensions of the structures.
     * @param structures List of structures; adjacent structures in the list will be adjacent in the circle.
     * @param spacing The desired spacing between each placed structure on the ring.
     * @return The minimum radius required.
     */
    public static int computeMinimumRadius(List<BlockStructure> structures, double spacing) {
        double largestTangentDistance = CircleStructureGenerator.computeLargestTangentDistance(structures);

        return CircleStructureGenerator.calculateRadius(structures.size(), largestTangentDistance + spacing);
    }

    /**
     * Compute the largest possible distance between two tangent structures of a given structure list.
     * @param structures A list of structures of which the largest possible tangent distance should be computed.
     * @return The largest possible distance of two tangent elements of the structure list.
     */
    public static double computeLargestTangentDistance(List<BlockStructure> structures) {
        if (structures.isEmpty()) return 0;

        // find the maximal width and length of the structure bounds

        int maxWidth = Integer.MIN_VALUE;
        int maxLength = Integer.MIN_VALUE;

        for (BlockStructure structure : structures) {
            int width = structure.getWidth();
            int length = structure.getLength();

            if (width > maxWidth) {
                maxWidth = width;
            }

            if (length > maxLength) {
                maxLength = length;
            }
        }

        // determine a suitable spacing that two adjacent structures must have in order not to collide
        // for that, find the largest possible horizontal diagonal distance from the center of a structure to an edge
        return sqrt(pow(maxWidth, 2) + pow(maxLength, 2));
    }

    /**
     * Calculates the radius so that two adjacent points on a circle are apart a given spacing.
     * @param structureCount The amount of structures that are to be placed evenly on a circle.
     *                       Used to determine the angular distance between the points on a circle.
     * @param spacing The distance that should be between two adjacent points on a circle.
     * @return The radius that a circle must have, so that two adjacent points are apart the given spacing.
     */
    public static double calculateRadiusExact(int structureCount, double spacing) {
        if (structureCount < 2) {
            return 0;
        }

        double angle = 2 * PI / structureCount;

        // two points A := (0, r) and B := (r * sin(angle), r * cos(angle))
        // distance between points: spacing = sqrt((r * sin(angle))² + (r * cos(angle) - r)²)
        // rearrange, so that r = ± sqrt(spacing² / (sin²(angle) + (cos(angle) - 1)²))

        return sqrt(pow(spacing, 2) / (pow(sin(angle), 2) + pow(cos(angle) - 1, 2)));
    }

    /**
     * Calculates the radius so that two adjacent points on a circle are apart a given spacing.
     * @param structureCount The amount of structures that are to be placed evenly on a circle.
     *                       Used to determine the angular distance between the points on a circle.
     * @param spacing The distance that should be between two adjacent points on a circle.
     * @return The integer radius that a circle must have, so that two adjacent points are apart the given spacing.
     */
    public static int calculateRadius(int structureCount, double spacing) {
        return (int) ceil(calculateRadiusExact(structureCount, spacing));
    }

    public static Vec2i[] generateHorizontalOffsets(List<BlockStructure> structures, final int minRadius) {
        return generateHorizontalOffsetsRadius(structures, minRadius).offsets();
    }

    public static OffsetResult generateHorizontalOffsetsRadius(List<BlockStructure> structures, final int minRadius) {
        /*
        The idea of the algorithm is the following:
        - start with the min radius
        - place every structure evenly on a circle
        - find a radius that has no collisions and a radius without collisions
        - perform bisection to find the minimal radius that has no collisions
        */

        final int count = structures.size();
        final double angularStep = 2 * PI / count;
        final BoxCollisionDetector collisionDetector = new BoxCollisionDetector();
        final Vec2i.Mutable[] offsets = new Vec2i.Mutable[count];

        for (int i = 0; i < count; i++) {
            offsets[i] = new Vec2i.Mutable(0, 0);
        }

        int radius = minRadius;
        int left = -1;
        int right = -1;

        next: while (true) {
            collisionDetector.reset();

            for (int i = 0; i < count; i++) {
                double angle = i * angularStep;

                BlockStructure structure = structures.get(i);

                // center structure on the circle
                int x = (int) round(sin(angle) * radius) - structure.getWidth() / 2;
                int z = (int) round(cos(angle) * radius) - structure.getLength() / 2;

                offsets[i].set(x, z);

                // y doesn't matter as the structures are not placed above each other
                BlockBox bounds = StructureUtil.getBounds(structure).transform(AffineIntMatrix.makeTranslation(x, 0, z));

                if (!collisionDetector.add(bounds)) {
                    // there are collisions
                    left = radius;

                    if (left < right) {
                        // prevent infinite loop
                        if (left == right - 1) {
                            left = right;
                        }

                        radius = (left + right) / 2;
                    } else {
                        if (radius <= 0) {
                            radius = 1;
                        } else {
                            radius *= 2;
                        }
                    }

                    continue next;
                }
            }

            right = radius;

            // check if we have found a collision radius yet
            if (left == -1 && radius > minRadius) {
                radius = Math.max(minRadius, radius / 2);
                continue;
            }

            // there were no collisions, check if we found the best radius
            if (left == right || left == right - 1 || radius == minRadius) {
                return new OffsetResult(offsets, radius);
            }

            // perform bisection by jumping to the middle of the two boundaries
            radius = (left + right) / 2;
        }
    }

    public interface PositionFunction {
        BlockPos compute(int index, BlockStructure structure, Vec2i circleOffset);
    }

    public record OffsetResult(Vec2i[] offsets, int radius) {}
}

package work.lclpnet.ap2.impl.util.world;

import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.collision.BoxCollisionDetector;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.math.Vec2i;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;

import static java.lang.Math.*;

public class CircleStructureGenerator {

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
    public static int calculateRadius(int structureCount, double spacing) {
        if (structureCount < 2) {
            return 0;
        }

        double angle = 2 * PI / structureCount;

        // two points A := (0, r) and B := (r * sin(angle), r * cos(angle))
        // distance between points: spacing = sqrt((r * sin(angle))² + (r * cos(angle) - r)²)
        // rearrange, so that r = ± sqrt(spacing² / (sin²(angle) + (cos(angle) - 1)²))

        double radius = sqrt(pow(spacing, 2) / (pow(sin(angle), 2) + pow(cos(angle) - 1, 2)));

        return (int) ceil(radius);
    }

    public static Vec2i[] generateHorizontalOffsets(List<BlockStructure> structures, int minRadius) {
        // the idea of the algorithm is the following:
        // 1. set current radius = minimum radius, searching = false
        // 2. try to place every structure evenly on a circle with the current radius
        // 3. set collisions = whether there are collisions between the structures
        // 4. if collisions || searching, goto 5. otherwise terminate with the offsets
        // 5. set searching = true
        // 6. if collisions, set prevRadius = radius, radius = 2 * radius then goto 2. otherwise goto 7
        // 7. set tmp = radius, radius = (radius + prevRadius) / 2, prevRadius = tmp
        // 8. if radius == prevRadius, terminate with offsets
        // 9. goto 2

        final int count = structures.size();
        final double angularStep = 2 * PI / count;
        final BoxCollisionDetector collisionDetector = new BoxCollisionDetector();
        final Vec2i.Mutable[] offsets = new Vec2i.Mutable[count];

        for (int i = 0; i < count; i++) {
            offsets[i] = new Vec2i.Mutable(0, 0);
        }

        int radius = minRadius;
        boolean searching = false;
        boolean lastWasFine = false;

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
                    // there are collisions, check if the last iteration was fine
                    if (lastWasFine) {
                        // we have found the best solution
                        radius = (radius << 2) / 3;
                        searching = false;
                    } else {
                        // double the radius and search for better solution
                        radius <<= 1;
                        searching = true;
                    }

                    continue next;
                }
            }

            // there were no collisions, check if we are not searching for a better solution, we are done
            if (!searching) {
                return offsets;
            }

            // the radius was doubled in the last iteration, now choose 3/4 of the current radius
            lastWasFine = true;
            radius = 3 * radius >> 2;
        }
    }
}

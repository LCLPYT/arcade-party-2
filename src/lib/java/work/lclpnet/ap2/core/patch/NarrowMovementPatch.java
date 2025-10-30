package work.lclpnet.ap2.core.patch;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static java.lang.Math.*;
import static net.minecraft.util.math.Direction.Axis.X;
import static net.minecraft.util.math.Direction.Axis.Z;


public class NarrowMovementPatch {

    @Nullable
    public static Vec3d getNodePosition(Entity entity, int x, int y, int z) {
        double hitBoxOffset = ((int) (entity.getWidth() + 1.0F)) * 0.5;

        // default node position
        double dx = x + hitBoxOffset;
        double dz = z + hitBoxOffset;

        Box boxAtNodePos = entity.getDimensions(entity.getPose()).getBoxAt(dx, y, dz);

        World world = entity.getEntityWorld();
        var blockCollisions = world.getBlockCollisions(entity, boxAtNodePos);

        for (VoxelShape collision : blockCollisions) {
            // calculate amount of intersection on each axis (overlap distance)
            double collisionMinX = collision.getMin(X);
            double collisionMinZ = collision.getMin(Z);
            double overlapX = min(collision.getMax(X), boxAtNodePos.maxX) - max(collisionMinX, boxAtNodePos.minX);
            double overlapZ = min(collision.getMax(Z), boxAtNodePos.maxZ) - max(collisionMinZ, boxAtNodePos.minZ);

            // if overlap is about the same along both axes, the collision cannot be resolved without error
            if (abs(overlapX - overlapZ) < 0.1) continue;

            int mtvX = 0, mtvZ = 0;
            double minOverlap = Double.MAX_VALUE;

            if (overlapX > 0 && overlapX < minOverlap) {
                mtvX = boxAtNodePos.minX < collisionMinX ? -1 : 1;
                minOverlap = overlapX;
            }

            if (overlapZ > 0 && overlapZ < minOverlap) {
                mtvX = 0;
                mtvZ = boxAtNodePos.minZ < collisionMinZ ? -1 : 1;
                minOverlap = overlapZ;
            }

            if (minOverlap >= Double.MAX_VALUE) continue;

            // small buffer
            minOverlap += 0.125;

            Vec3d adjusted = new Vec3d(
                    dx + mtvX * minOverlap,
                    y,
                    dz + mtvZ * minOverlap
            );

            // check if adjusted box still collides
            Box adjustedBox = entity.getType().getDimensions().getBoxAt(adjusted.x, adjusted.y, adjusted.z);

            if (world.getBlockCollisions(entity, adjustedBox).iterator().hasNext()) {
                // there are collisions at the adjusted position, abort
                continue;
            }

            return adjusted;
        }

        return null;
    }
}

package work.lclpnet.ap2.core.patch;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import static java.lang.Math.*;
import static net.minecraft.core.Direction.Axis.X;
import static net.minecraft.core.Direction.Axis.Z;


public class NarrowMovementPatch {

    @Nullable
    public static Vec3 getNodePosition(Entity entity, int x, int y, int z) {
        double hitBoxOffset = ((int) (entity.getBbWidth() + 1.0F)) * 0.5;

        // default node position
        double dx = x + hitBoxOffset;
        double dz = z + hitBoxOffset;

        AABB boxAtNodePos = entity.getDimensions(entity.getPose()).makeBoundingBox(dx, y, dz);

        Level world = entity.level();
        var blockCollisions = world.getBlockCollisions(entity, boxAtNodePos);

        for (VoxelShape collision : blockCollisions) {
            // calculate amount of intersection on each axis (overlap distance)
            double collisionMinX = collision.min(X);
            double collisionMinZ = collision.min(Z);
            double overlapX = min(collision.max(X), boxAtNodePos.maxX) - max(collisionMinX, boxAtNodePos.minX);
            double overlapZ = min(collision.max(Z), boxAtNodePos.maxZ) - max(collisionMinZ, boxAtNodePos.minZ);

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

            Vec3 adjusted = new Vec3(
                    dx + mtvX * minOverlap,
                    y,
                    dz + mtvZ * minOverlap
            );

            // check if adjusted box still collides
            AABB adjustedBox = entity.getType().getDimensions().makeBoundingBox(adjusted.x, adjusted.y, adjusted.z);

            if (world.getBlockCollisions(entity, adjustedBox).iterator().hasNext()) {
                // there are collisions at the adjusted position, abort
                continue;
            }

            return adjusted;
        }

        return null;
    }
}

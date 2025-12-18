package work.lclpnet.ap2.impl.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Collection;
import java.util.function.Predicate;

public class RayCastUtil {

    private RayCastUtil() {}

    public static HitResult raycast(ServerPlayer player, double maxDistance, ClipContext.Block shapeType,
                                    ClipContext.Fluid fluidHandling, CollisionContext shapeContext, Predicate<Entity> filter) {
        return raycast(player.level(), player.getEyePosition(), player.getLookAngle(), maxDistance, shapeType,
                fluidHandling, shapeContext, filter);
    }

    /**
     * Ray cast both blocks and entities.
     * The closest intersection will be returned.
     * @param world The world to perform the raycast in.
     * @param start The ray start position.
     * @param direction The ray direction.
     * @param maxDistance The maximum ray travel distance, after which the ray misses.
     * @param shapeType The block shape type to intersect against.
     * @param fluidHandling The fluid handle mode to use for block intersection tests.
     * @param shapeContext The shape context to use for intersection tests. If there is no context, use <code>ShapeContext.absent()</code>.
     * @param filter A filter that checks if an entity is eligible for intersection. If the predicate returns false for an entity, it won't be considered when intersecting with the ray.
     * @return A raycast {@link HitResult} that is either of type BLOCK, ENTITY or MISS.
     */
    public static HitResult raycast(Level world, Vec3 start, Vec3 direction, double maxDistance,
                                    ClipContext.Block shapeType, ClipContext.Fluid fluidHandling,
                                    CollisionContext shapeContext, Predicate<Entity> filter) {

        HitResult blockHit = raycastBlocks(world, start, direction, maxDistance, shapeType, fluidHandling, shapeContext);

        double blockHitDistance = maxDistance;

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            blockHitDistance = start.distanceTo(blockHit.getLocation());
        }

        HitResult entityHit = raycastEntities(world, start, direction, blockHitDistance, filter);

        if (blockHit.getType() == HitResult.Type.MISS) {
            return entityHit;
        }

        if (entityHit.getType() == HitResult.Type.MISS) {
            return blockHit;
        }

        return start.distanceToSqr(entityHit.getLocation()) < blockHitDistance * blockHitDistance ? entityHit : blockHit;
    }

    public static BlockHitResult raycastBlocks(BlockGetter world, Vec3 start, Vec3 direction, double maxDistance,
                                               ClipContext.Block shapeType, ClipContext.Fluid fluidHandling,
                                               CollisionContext shapeContext) {

        Vec3 end = start.add(direction.scale(maxDistance));

        return world.clip(new ClipContext(start, end, shapeType, fluidHandling, shapeContext));
    }

    public static HitResult raycastEntities(Level world, Vec3 start, Vec3 direction, double maxDistance, Predicate<Entity> filter) {
        if (maxDistance < 0.d) {
            return new MissHitResult(start);
        }

        Vec3 dir = direction.normalize().scale(maxDistance);

        // in world space
        AABB box = new AABB(start, start).inflate(dir.x(), dir.y(), dir.z());

        Collection<Entity> entities = world.getEntities((Entity) null, box, filter);
        Entity hitEntity = null;
        Vec3 nearestHit = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        Vec3 end = start.add(dir);

        for (Entity entity : entities) {
            AABB boundingBox = entity.getBoundingBox();
            var hitPos = boundingBox.clip(start, end);

            if (hitPos.isEmpty()) continue;

            double distanceSq = start.distanceToSqr(hitPos.get());

            if (distanceSq < nearestDistanceSq) {
                hitEntity = entity;
                nearestHit = hitPos.get();
                nearestDistanceSq = distanceSq;
            }
        }

        if (hitEntity == null) {
            return new MissHitResult(end);
        }

        return new EntityHitResult(hitEntity, nearestHit);
    }

    public static class MissHitResult extends HitResult {

        protected MissHitResult(Vec3 pos) {
            super(pos);
        }

        @Override
        public Type getType() {
            return Type.MISS;
        }
    }
}

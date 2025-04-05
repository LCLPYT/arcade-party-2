package work.lclpnet.ap2.impl.util;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.function.Predicate;

public class RayCastUtil {

    private RayCastUtil() {}

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
    public static HitResult raycast(World world, Vec3d start, Vec3d direction, double maxDistance,
                                    RaycastContext.ShapeType shapeType, RaycastContext.FluidHandling fluidHandling,
                                    ShapeContext shapeContext, Predicate<Entity> filter) {

        HitResult blockHit = raycastBlocks(world, start, direction, maxDistance, shapeType, fluidHandling, shapeContext);

        double blockHitDistance = maxDistance;

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            blockHitDistance = start.distanceTo(blockHit.getPos());
        }

        HitResult entityHit = raycastEntities(world, start, direction, blockHitDistance, filter);

        if (blockHit.getType() == HitResult.Type.MISS) {
            return entityHit;
        }

        if (entityHit.getType() == HitResult.Type.MISS) {
            return blockHit;
        }

        return start.squaredDistanceTo(entityHit.getPos()) < blockHitDistance * blockHitDistance ? entityHit : blockHit;
    }

    public static HitResult raycastBlocks(BlockView world, Vec3d start, Vec3d direction, double maxDistance,
                                          RaycastContext.ShapeType shapeType, RaycastContext.FluidHandling fluidHandling,
                                          ShapeContext shapeContext) {

        Vec3d end = start.add(direction.multiply(maxDistance));

        return world.raycast(new RaycastContext(start, end, shapeType, fluidHandling, shapeContext));
    }

    public static HitResult raycastEntities(World world, Vec3d start, Vec3d direction, double maxDistance, Predicate<Entity> filter) {
        if (maxDistance < 0.d) {
            return new MissHitResult(start);
        }

        Vec3d dir = direction.normalize().multiply(maxDistance);

        // in world space
        Box box = new Box(start, start).expand(dir.getX(), dir.getY(), dir.getZ());

        Collection<Entity> entities = world.getOtherEntities(null, box, filter);
        Entity hitEntity = null;
        Vec3d nearestHit = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        Vec3d end = start.add(dir);

        for (Entity entity : entities) {
            Box boundingBox = entity.getBoundingBox();
            var hitPos = boundingBox.raycast(start, end);

            if (hitPos.isEmpty()) continue;

            double distanceSq = start.squaredDistanceTo(hitPos.get());

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

        protected MissHitResult(Vec3d pos) {
            super(pos);
        }

        @Override
        public Type getType() {
            return Type.MISS;
        }
    }
}

package work.lclpnet.ap2.impl.ai;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import work.lclpnet.ap2.api.ai.PathFindingPredicate;
import work.lclpnet.ap2.core.patch.NarrowMovementPatch;

public class CollisionPathFindingPredicate implements PathFindingPredicate {

    private CollisionPathFindingPredicate() {}

    @Override
    public boolean canReach(int x, int y, int z, MobEntity entity, BlockPos from) {
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());

        var pos = NarrowMovementPatch.getNodePosition(entity, x, y, z);

        Box box;

        if (pos != null) {
            box = dimensions.getBoxAt(pos);
        } else {
            box = dimensions.getBoxAt(x + 0.5, y, z + 0.5);
        }

        var collisions = entity.getWorld().getBlockCollisions(entity, box);

        return !collisions.iterator().hasNext();
    }

    public static CollisionPathFindingPredicate getInstance() {
        return Holder.instance;
    }
    
    private static class Holder {
        private static final CollisionPathFindingPredicate instance = new CollisionPathFindingPredicate();
    }
}

package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.collision.UnionCollider;

import java.util.List;
import java.util.UUID;

public class CCBase {

    private final Collider bounds;
    private final BlockPos campfirePos;
    private final UUID entityUuid;

    public CCBase(List<BlockBox> bounds, BlockPos campfirePos, UUID entityUuid) {
        this.bounds = new UnionCollider(bounds.toArray(BlockBox[]::new));
        this.campfirePos = campfirePos;
        this.entityUuid = entityUuid;
    }

    public boolean isEntity(Entity entity) {
        return entityUuid.equals(entity.getUuid());
    }

    public BlockPos getCampfirePos() {
        return campfirePos;
    }

    public Collider getBounds() {
        return bounds;
    }

    public boolean isInside(double x, double y, double z) {
        return bounds.collidesWith(x, y, z);
    }
}

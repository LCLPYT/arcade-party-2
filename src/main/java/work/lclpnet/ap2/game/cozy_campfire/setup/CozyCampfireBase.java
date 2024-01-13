package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.List;
import java.util.UUID;

public class CozyCampfireBase {

    private final List<BlockBox> bounds;
    private final BlockPos campfirePos;
    private final UUID entityUuid;

    public CozyCampfireBase(List<BlockBox> bounds, BlockPos campfirePos, UUID entityUuid) {
        this.bounds = bounds;
        this.campfirePos = campfirePos;
        this.entityUuid = entityUuid;
    }

    public boolean isEntity(Entity entity) {
        return entityUuid.equals(entity.getUuid());
    }

    public BlockPos getCampfirePos() {
        return campfirePos;
    }

    public boolean isInside(double x, double y, double z) {
        return bounds.stream().anyMatch(box -> box.contains(x, y, z));
    }
}

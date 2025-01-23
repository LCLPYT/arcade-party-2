package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.collision.UnionCollider;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.List;
import java.util.UUID;

public record CCBase(
        Collider bounds,
        BlockPos campfirePos,
        UUID entityUuid,
        @Nullable BlockStructure doorSchematic,
        @Nullable BlockPos doorPos
) {

    public CCBase(List<BlockBox> bounds, BlockPos campfirePos, UUID entityUuid, @Nullable BlockStructure doorSchematic, @Nullable BlockPos doorPos) {
        this(new UnionCollider(bounds.toArray(BlockBox[]::new)), campfirePos, entityUuid, doorSchematic, doorPos);
    }

    public boolean isEntity(Entity entity) {
        return entityUuid.equals(entity.getUuid());
    }

    public boolean isInside(double x, double y, double z) {
        return bounds.collidesWith(x, y, z);
    }
}

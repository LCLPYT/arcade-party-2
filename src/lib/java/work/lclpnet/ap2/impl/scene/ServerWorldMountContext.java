package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.impl.util.EntityRef;

public record ServerWorldMountContext(ServerWorld world) implements MountContext {

    @Override
    public <T extends Entity> Resolvable<@Nullable T> spawn(@Nullable T entity, Object3d origin) {
        if (entity == null || !world.spawnEntity(entity)) {
            return Resolvable.none();
        }

        return new EntityRef<>(entity);
    }

    @Override
    public <T extends Entity> void remove(@Nullable T entity, Object3d origin) {
        if (entity != null) {
            entity.discard();
        }
    }
}

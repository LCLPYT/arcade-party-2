package work.lclpnet.ap2.impl.scene;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.Resolvable;
import work.lclpnet.ap2.impl.util.EntityRef;
import work.lclpnet.gaco.dynamic_entities.DynamicEntity;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;

/**
 * A mixed mount context that supports both real and dynamic entities.
 *
 * @param world                The server world.
 * @param dynamicEntityManager The dynamic entity manager.
 */
public record MixedMountContext(ServerWorld world, DynamicEntityManager dynamicEntityManager) implements MountContext {

    @Override
    public <T extends Entity> Resolvable<@Nullable T> spawn(@Nullable T entity, Object3d origin) {
        if (origin instanceof DynamicEntity dynamic) {
            dynamicEntityManager.add(dynamic);

            // dynamic entities are always loaded atm. therefore a constant ref is sufficient
            return Resolvable.constant(entity);
        }

        if (entity != null && world.spawnEntity(entity)) {
            return new EntityRef<>(entity);
        }

        return Resolvable.none();
    }

    @Override
    public <T extends Entity> void remove(@Nullable T entity, Object3d origin) {
        if (entity != null) {
            entity.discard();
        }

        if (origin instanceof DynamicEntity dynamic) {
           dynamicEntityManager.remove(dynamic);
        }
    }
}

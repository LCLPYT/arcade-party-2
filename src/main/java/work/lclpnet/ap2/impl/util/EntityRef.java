package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.Resolvable;

import java.util.UUID;

public record EntityRef<T extends Entity>(UUID uuid, World world, Class<T> type) implements Resolvable<@Nullable T> {

    @SuppressWarnings("unchecked")
    public EntityRef(T entity) {
        this(entity.getUuid(), entity.getWorld(), (Class<T>) entity.getClass());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public T resolve() {
        Entity entity = world.getEntity(uuid);

        if (type.isInstance(entity)) {
            return (T) entity;
        }

        return null;
    }
}

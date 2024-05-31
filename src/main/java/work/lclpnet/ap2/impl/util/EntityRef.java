package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record EntityRef<T extends Entity>(UUID uuid, ServerWorld world, Class<T> type) {

    @SuppressWarnings("unchecked")
    public EntityRef(T entity) {
        this(entity.getUuid(), requireServerWorld(entity.getWorld()), (Class<T>) entity.getClass());
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

    private static ServerWorld requireServerWorld(World world) {
        if (world instanceof ServerWorld serverWorld) {
            return serverWorld;
        }

        throw new IllegalArgumentException("ServerWorld is required");
    }
}

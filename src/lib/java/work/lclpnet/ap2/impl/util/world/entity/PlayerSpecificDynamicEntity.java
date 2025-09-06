package work.lclpnet.ap2.impl.util.world.entity;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PlayerSpecificDynamicEntity<T extends Entity> implements DynamicEntity {

    @Getter
    private final T entity;
    private final UUID viewerUuid;

    public PlayerSpecificDynamicEntity(T entity, UUID viewerUuid) {
        this.entity = entity;
        this.viewerUuid = viewerUuid;
    }

    @Override
    public Vec3d getPosition() {
        return entity.getPos();
    }

    @Override
    public @Nullable T getEntity(ServerPlayerEntity player) {
        return player.getUuid().equals(viewerUuid) ? entity : null;
    }

    @Override
    public void cleanup(ServerPlayerEntity player) {}
}

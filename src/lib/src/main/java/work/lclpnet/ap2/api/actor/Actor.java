package work.lclpnet.ap2.api.actor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface Actor {

    ActorType<?> getType();

    Vec3 getPosition();

    void setPosition(Vec3 pos);

    ServerLevel getWorld();

    default void onSpawn() {}

    default void onRemove() {}

    default @Nullable ActorData<?> createData() {
        return null;
    }
}

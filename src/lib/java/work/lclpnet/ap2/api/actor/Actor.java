package work.lclpnet.ap2.api.actor;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public interface Actor {

    ActorType<?> getType();

    Vec3d getPosition();

    void setPosition(Vec3d pos);

    ServerWorld getWorld();

    default void onSpawn() {}

    default void onRemove() {}

    default @Nullable ActorData<?> createData() {
        return null;
    }
}

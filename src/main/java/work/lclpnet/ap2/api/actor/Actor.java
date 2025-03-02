package work.lclpnet.ap2.api.actor;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public interface Actor {

    Vec3d getPosition();

    void setPosition(Vec3d pos);

    ServerWorld getWorld();

    default void onSpawn() {}

    default void onRemove() {}
}

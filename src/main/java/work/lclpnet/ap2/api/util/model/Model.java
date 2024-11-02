package work.lclpnet.ap2.api.util.model;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public interface Model {

    void spawn(ServerWorld world, Vec3d pos);
}

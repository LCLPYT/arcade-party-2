package work.lclpnet.ap2.impl.util.world.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public interface DynamicEntity {

    Vec3d getPosition();

    Entity getEntity(ServerPlayerEntity player);

    void cleanup(ServerPlayerEntity player);
}

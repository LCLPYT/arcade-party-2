package work.lclpnet.ap2.api.game;

import net.minecraft.server.world.ServerWorld;

@FunctionalInterface
public interface MapReady {

    void onReady(ServerWorld world);
}

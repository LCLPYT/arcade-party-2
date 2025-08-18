package work.lclpnet.ap2.api.base;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;

public interface WorldBorderManager {

    WorldBorder getWorldBorder();

    void setupWorldBorder(ServerWorld world);

    void resetWorldBorder();
}

package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.server.network.ServerPlayerEntity;

public interface VirtualScoreboardObjective {

    void add(ServerPlayerEntity player);

    void remove(ServerPlayerEntity player);

    void update(ServerPlayerEntity player);

    void unload();
}

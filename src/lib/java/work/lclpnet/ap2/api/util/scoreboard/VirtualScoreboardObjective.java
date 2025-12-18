package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.server.level.ServerPlayer;

public interface VirtualScoreboardObjective {

    void add(ServerPlayer player);

    void remove(ServerPlayer player);

    void update(ServerPlayer player);

    void unload();
}

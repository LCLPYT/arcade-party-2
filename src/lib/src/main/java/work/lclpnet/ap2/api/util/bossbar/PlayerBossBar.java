package work.lclpnet.ap2.api.util.bossbar;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

public interface PlayerBossBar {

    ServerBossEvent getBossBar(ServerPlayer player);

    void remove(ServerPlayer player);

    default void add(ServerPlayer player) {
        getBossBar(player).addPlayer(player);
    }
}

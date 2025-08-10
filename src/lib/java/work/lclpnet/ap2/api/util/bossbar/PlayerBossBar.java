package work.lclpnet.ap2.api.util.bossbar;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerBossBar {

    ServerBossBar getBossBar(ServerPlayerEntity player);

    void remove(ServerPlayerEntity player);

    default void add(ServerPlayerEntity player) {
        getBossBar(player).addPlayer(player);
    }
}

package work.lclpnet.ap2.impl.util.bossbar;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.ap2.api.util.bossbar.PlayerBossBar;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

public class SimplePlayerBossBar implements PlayerBossBar {

    private final Function<ServerPlayer, ServerBossEvent> factory;
    private final Map<UUID, ServerBossEvent> bossBars = new WeakHashMap<>();

    public SimplePlayerBossBar(Function<ServerPlayer, ServerBossEvent> factory) {
        this.factory = factory;
    }

    @Override
    public ServerBossEvent getBossBar(ServerPlayer player) {
        return bossBars.computeIfAbsent(player.getUUID(), uuid -> factory.apply(player));
    }

    @Override
    public void remove(ServerPlayer player) {
        ServerBossEvent bossBar = bossBars.remove(player.getUUID());
        if (bossBar == null) return;

        bossBar.removePlayer(player);

        // Note: boss bar unregistering is not handled by this class
    }
}

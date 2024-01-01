package work.lclpnet.ap2.impl.util.bossbar;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.util.bossbar.PlayerBossBar;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

public class SimplePlayerBossBar implements PlayerBossBar {

    private final Function<ServerPlayerEntity, ServerBossBar> factory;
    private final Map<UUID, ServerBossBar> bossBars = new WeakHashMap<>();

    public SimplePlayerBossBar(Function<ServerPlayerEntity, ServerBossBar> factory) {
        this.factory = factory;
    }

    @Override
    public ServerBossBar getBossBar(ServerPlayerEntity player) {
        return bossBars.computeIfAbsent(player.getUuid(), uuid -> factory.apply(player));
    }

    @Override
    public void remove(ServerPlayerEntity player) {
        ServerBossBar bossBar = bossBars.remove(player.getUuid());
        if (bossBar == null) return;

        bossBar.removePlayer(player);

        // Note: boss bar unregistering is not handled by this class
    }
}

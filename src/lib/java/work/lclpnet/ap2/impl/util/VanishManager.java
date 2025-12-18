package work.lclpnet.ap2.impl.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.hook.PlayerCanTrackCallback;
import work.lclpnet.ap2.core.hook.PlayerListEntriesOnJoinCallback;
import work.lclpnet.ap2.core.mixin.ServerChunkCacheAccessor;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.ServerMessageHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishManager {

    private final MinecraftServer server;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishManager(MinecraftServer server) {
        this.server = server;
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(PlayerListEntriesOnJoinCallback.HOOK, players -> players.stream()
                .filter(player -> !isVanished(player))
                .toList());

        hooks.registerHook(ServerMessageHooks.ALLOW_CHAT_MESSAGE, (message, sender, params) -> {
            if (isVanished(sender)) {
                for (ServerPlayer player : PlayerLookup.all(server)) {
                    player.connection.sendDisguisedChatMessage(message.decoratedContent(), params);
                }
                return false;
            }

            return true;
        });

        hooks.registerHook(PlayerCanTrackCallback.HOOK, (player, entity) -> {
            if (!(entity instanceof ServerPlayer subjectPlayer) || subjectPlayer == player) return true;

            return !isVanished(subjectPlayer);
        });
    }

    public synchronized void vanish(ServerPlayer player) {
        synchronized (this) {
            if (!vanished.add(player.getUUID())) return;
        }

        updateTrackingOf(player);
    }

    public void show(ServerPlayer player) {
        synchronized (this) {
            if (!vanished.remove(player.getUUID())) return;
        }

        updateTrackingOf(player);
    }

    private void updateTrackingOf(ServerPlayer player) {
        var chunkLoadingManager = ((ServerChunkCacheAccessor) player.level().getChunkSource()).getChunkMap();
        chunkLoadingManager.move(player);
    }

    public synchronized boolean isVanished(ServerPlayer player) {
        return vanished.contains(player.getUUID());
    }

    public void destroy() {
        UUID[] vanished;

        synchronized (this) {
            vanished = this.vanished.toArray(UUID[]::new);
        }

        PlayerList playerManager = server.getPlayerList();

        for (UUID uuid : vanished) {
            ServerPlayer player = playerManager.getPlayer(uuid);

            if (player != null) {
                show(player);
            }
        }
    }

    public static VanishManager setup(MiniGameHandle gameHandle) {
        var vanishManager = new VanishManager(gameHandle.getServer());

        gameHandle.whenDone(vanishManager::destroy);

        vanishManager.init(gameHandle.getHooks());

        return vanishManager;
    }
}

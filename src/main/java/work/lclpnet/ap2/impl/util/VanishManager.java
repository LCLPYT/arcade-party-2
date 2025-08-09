package work.lclpnet.ap2.impl.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.core.hook.PlayerListEntriesOnJoinCallback;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.ServerMessageHooks;

import java.util.HashSet;
import java.util.List;
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
                for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                    player.networkHandler.sendProfilelessChatMessage(message.getContent(), params);
                }
                return false;
            }

            return true;
        });
    }

    public synchronized void vanish(ServerPlayerEntity player) {
        synchronized (this) {
            if (!vanished.add(player.getUuid())) return;
        }

        sendToOtherPlayers(new PlayerRemoveS2CPacket(List.of(player.getUuid())), player);
    }

    public void show(ServerPlayerEntity player) {
        synchronized (this) {
            if (!vanished.remove(player.getUuid())) return;
        }

        sendToOtherPlayers(PlayerListS2CPacket.entryFromPlayer(List.of(player)), player);
    }

    private void sendToOtherPlayers(Packet<?> packet, ServerPlayerEntity exclude) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            if (player.networkHandler == exclude.networkHandler) continue;

            System.out.println("sending disconnect of " + exclude.getNameForScoreboard() + " to " + player.getNameForScoreboard());
            player.networkHandler.sendPacket(packet);
        }
    }

    public synchronized boolean isVanished(ServerPlayerEntity player) {
        return vanished.contains(player.getUuid());
    }

    public void destroy() {
        UUID[] vanished;

        synchronized (this) {
            vanished = this.vanished.toArray(UUID[]::new);
        }

        PlayerManager playerManager = server.getPlayerManager();

        for (UUID uuid : vanished) {
            ServerPlayerEntity player = playerManager.getPlayer(uuid);

            if (player != null) {
                show(player);
            }
        }
    }

    public static VanishManager setup(MiniGameHandle gameHandle) {
        var vanishManager = new VanishManager(gameHandle.getServer());

        gameHandle.whenDone(vanishManager::destroy);

        vanishManager.init(gameHandle.getHookRegistrar());

        return vanishManager;
    }
}

package work.lclpnet.ap2.api.game.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record PlayerRef(UUID uuid, String name) {

    @Nullable
    public ServerPlayerEntity resolve(MinecraftServer server) {
        return resolve(server.getPlayerManager());
    }

    @Nullable
    public ServerPlayerEntity resolve(PlayerManager playerManager) {
        return playerManager.getPlayer(uuid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerRef playerRef = (PlayerRef) o;
        return Objects.equals(uuid, playerRef.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    public static PlayerRef create(ServerPlayerEntity player) {
        return new PlayerRef(player.getUuid(), player.getNameForScoreboard());
    }
}

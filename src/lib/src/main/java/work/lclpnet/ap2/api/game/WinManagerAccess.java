package work.lclpnet.ap2.api.game;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public interface WinManagerAccess {

    void draw();

    void win(ServerPlayer player);

    void win(Set<ServerPlayer> players);
}

package work.lclpnet.ap2.api.game;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

public interface WinManagerAccess {

    void draw();

    void win(ServerPlayerEntity player);

    void win(Set<ServerPlayerEntity> players);
}

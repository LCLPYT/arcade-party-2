package work.lclpnet.ap2.api.event;

import net.minecraft.server.network.ServerPlayerEntity;

public interface IntScoreEvent {

    void accept(ServerPlayerEntity player, int score);
}

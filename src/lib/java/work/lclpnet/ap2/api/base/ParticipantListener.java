package work.lclpnet.ap2.api.base;

import net.minecraft.server.level.ServerPlayer;

public interface ParticipantListener {

    void participantRemoved(ServerPlayer player);
}

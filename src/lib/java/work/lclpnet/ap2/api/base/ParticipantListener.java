package work.lclpnet.ap2.api.base;

import net.minecraft.server.network.ServerPlayerEntity;

public interface ParticipantListener {

    void participantRemoved(ServerPlayerEntity player);
}

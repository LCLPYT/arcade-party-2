package work.lclpnet.ap2.api.game;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

public interface GameStartContext {

    Set<ServerPlayerEntity> getParticipants();

    default int getParticipantCount() {
        return getParticipants().size();
    }
}

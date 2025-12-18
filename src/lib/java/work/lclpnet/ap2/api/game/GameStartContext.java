package work.lclpnet.ap2.api.game;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public interface GameStartContext {

    Set<ServerPlayer> getParticipants();

    default int getParticipantCount() {
        return getParticipants().size();
    }
}

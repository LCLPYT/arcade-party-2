package work.lclpnet.ap2.api.base;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

public interface PlayerManager extends Participants {

    /**
     * Adds every non-permanent spectator to the participants list.
     */
    void reset();

    void enterFinale(Set<? extends ServerPlayerEntity> finalists);

    void addPermanentSpectator(ServerPlayerEntity player);

    void removePermanentSpectator(ServerPlayerEntity player);

    void removeParticipant(ServerPlayerEntity player);

    void bind(ParticipantListener listener);
}

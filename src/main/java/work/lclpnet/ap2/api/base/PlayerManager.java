package work.lclpnet.ap2.api.base;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

public interface PlayerManager extends Participants {

    boolean offer(ServerPlayerEntity player);

    void startPreparation();

    void startMiniGame();

    void enterFinale(Set<? extends ServerPlayerEntity> finalists);

    void addPermanentSpectator(ServerPlayerEntity player);

    void removePermanentSpectator(ServerPlayerEntity player);

    boolean isPermanentSpectator(ServerPlayerEntity player);

    void bind(ParticipantListener listener);
}

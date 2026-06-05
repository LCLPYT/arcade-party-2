package work.lclpnet.ap2.api.base;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public interface PlayerManager extends Participants {

    boolean offer(ServerPlayer player);

    void startPreparation();

    void startMiniGame();

    void enterFinale(Set<? extends ServerPlayer> finalists);

    void addPermanentSpectator(ServerPlayer player);

    void removePermanentSpectator(ServerPlayer player);

    boolean isPermanentSpectator(ServerPlayer player);

    void bind(ParticipantListener listener);

    void leaveFinale();

    boolean isFinale();
}

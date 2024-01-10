package work.lclpnet.ap2.impl.game;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.game.MiniGameHandle;

public abstract class DefaultTeamGameInstance extends BaseGameInstance implements ParticipantListener {

    public DefaultTeamGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public ParticipantListener getParticipantListener() {
        return this;
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        // TODO implement
    }
}

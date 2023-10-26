package work.lclpnet.ap2.api.game;

import work.lclpnet.ap2.api.base.ParticipantListener;

public interface MiniGameInstance {

    void start();

    ParticipantListener getParticipantListener();
}

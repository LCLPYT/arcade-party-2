package work.lclpnet.ap2.api.game;

import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.kibu.scheduler.Ticks;

public interface MiniGameInstance {

    void start();

    ParticipantListener getParticipantListener();

    default int getMaxDurationTicks() {
        return Ticks.minutes(15);
    }
}

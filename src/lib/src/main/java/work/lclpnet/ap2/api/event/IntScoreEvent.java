package work.lclpnet.ap2.api.event;

import org.jetbrains.annotations.NotNull;

public interface IntScoreEvent<Type> {

    void accept(@NotNull Type player, int score);
}

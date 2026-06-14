package work.lclpnet.ap2.api.event;

import org.jetbrains.annotations.NotNull;

public interface IntScoreEventSource<Type> {

    void register(@NotNull IntScoreEvent<Type> listener);
}

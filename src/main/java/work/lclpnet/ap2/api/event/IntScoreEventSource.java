package work.lclpnet.ap2.api.event;

public interface IntScoreEventSource<Type> {

    void register(IntScoreEvent<Type> listener);
}

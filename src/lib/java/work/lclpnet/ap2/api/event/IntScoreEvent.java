package work.lclpnet.ap2.api.event;

public interface IntScoreEvent<Type> {

    void accept(Type player, int score);
}

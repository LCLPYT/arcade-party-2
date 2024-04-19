package work.lclpnet.ap2.api.game.sink;

public interface IntDataSink<T> {

    void addScore(T subject, int add);

    int getScore(T subject);
}

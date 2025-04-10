package work.lclpnet.ap2.api.game.data;

public interface GameWinnersFactory<T, Ref extends SubjectRef> {

    GameWinners<Ref> create(DataContainer<T, Ref> data);
}

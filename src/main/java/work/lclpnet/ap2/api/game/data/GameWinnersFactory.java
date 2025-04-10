package work.lclpnet.ap2.api.game.data;

public interface GameWinnersFactory<T, Ref extends SubjectRef> {

    GenericGameResult<Ref> create(DataContainer<T, Ref> data);
}

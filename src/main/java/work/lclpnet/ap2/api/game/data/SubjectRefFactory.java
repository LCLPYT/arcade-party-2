package work.lclpnet.ap2.api.game.data;

public interface SubjectRefFactory<T, Ref> {

    Ref create(T subject);
}

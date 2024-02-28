package work.lclpnet.ap2.api.game.data;

import java.util.Set;

public interface GameWinnersFactory<T, Ref extends SubjectRef> {

    GameWinners<Ref> create(Set<T> winners);
}

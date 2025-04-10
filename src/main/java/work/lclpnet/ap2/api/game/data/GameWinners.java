package work.lclpnet.ap2.api.game.data;

import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;

import java.util.Set;

public interface GameWinners<Ref extends SubjectRef> {

    Set<PlayerRef> getWinningPlayers();

    Set<Ref> getWinningSubjects();

    MiniGameResults getResults();
}

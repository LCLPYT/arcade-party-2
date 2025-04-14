package work.lclpnet.ap2.api.game.data;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;

import java.util.List;
import java.util.Set;

public interface GenericGameResult<Ref extends SubjectRef> {

    Set<PlayerRef> getWinningPlayers();

    Set<Ref> getWinningSubjects();

    List<ObjectIntPair<PlayerRef>> getPlayerResults();

    List<ObjectIntPair<Ref>> getSubjectResults();
}

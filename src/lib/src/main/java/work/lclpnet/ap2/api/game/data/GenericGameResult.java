package work.lclpnet.ap2.api.game.data;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;

import java.util.List;
import java.util.Set;

public interface GenericGameResult<Ref extends SubjectRef> {

    @NotNull Set<@NotNull PlayerRef> getWinningPlayers();

    @NotNull Set<@NotNull Ref> getWinningSubjects();

    @NotNull List<@NotNull ObjectIntPair<@NotNull PlayerRef>> getPlayerResults();

    @NotNull List<@NotNull ObjectIntPair<@NotNull Ref>> getSubjectResults();
}

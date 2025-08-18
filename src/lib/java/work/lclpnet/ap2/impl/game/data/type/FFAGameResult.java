package work.lclpnet.ap2.impl.game.data.type;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.GenericGameResult;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FFAGameResult implements GenericGameResult<PlayerRef> {

    private final List<ObjectIntPair<PlayerRef>> results;
    private final Set<PlayerRef> refs;

    public FFAGameResult(DataContainer<ServerPlayerEntity, PlayerRef> data) {
        var byRank = data.streamEntriesRanked().toList();

        this.results = byRank.stream()
                .flatMap(Collection::stream)
                .toList();

        this.refs = byRank.isEmpty()
                ? Set.of()
                : byRank.getFirst().stream()
                .map(ObjectIntPair::left)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<PlayerRef> getWinningPlayers() {
        return refs;
    }

    @Override
    public Set<PlayerRef> getWinningSubjects() {
        return refs;
    }

    @Override
    public List<ObjectIntPair<PlayerRef>> getPlayerResults() {
        return results;
    }

    @Override
    public List<ObjectIntPair<PlayerRef>> getSubjectResults() {
        return results;
    }
}

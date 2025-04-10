package work.lclpnet.ap2.impl.game.data.type;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.GameWinners;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class PlayerGameWinners implements GameWinners<PlayerRef> {

    private final MiniGameResults results;
    private final Set<PlayerRef> refs;

    public PlayerGameWinners(DataContainer<ServerPlayerEntity, PlayerRef> data) {
        var byRank = data.streamEntriesRanked().toList();

        var resultMap = byRank.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(
                        Pair::left,
                        playerRank -> new MiniGameResults.PlayerResult(playerRank.left(), playerRank.rightInt())
                ));

        this.results = new MiniGameResults(resultMap);

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
    public MiniGameResults getResults() {
        return results;
    }
}

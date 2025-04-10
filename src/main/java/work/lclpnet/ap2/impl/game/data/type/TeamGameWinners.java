package work.lclpnet.ap2.impl.game.data.type;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.GameWinners;
import work.lclpnet.ap2.api.game.data.SubjectRefResolver;
import work.lclpnet.ap2.api.game.team.Team;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TeamGameWinners implements GameWinners<TeamRef> {

    private final MiniGameResults results;
    private final Set<PlayerRef> players;
    private final Set<TeamRef> refs;

    public TeamGameWinners(DataContainer<Team, TeamRef> data, SubjectRefResolver<Team, TeamRef> refResolver) {
        var byRank = data.streamEntriesRanked().toList();

        var resultMap = byRank.stream()
                .flatMap(Collection::stream)
                .flatMap(teamRank -> {
                    Team team = refResolver.resolve(teamRank.key());

                    if (team == null) {
                        return Stream.empty();
                    }

                    return team.getPlayers().stream()
                            .map(PlayerRef::create)
                            .map(ref -> ObjectIntPair.of(ref, teamRank.rightInt()));
                })
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

        this.players = this.refs.stream()
                .map(refResolver::resolve)
                .filter(Objects::nonNull)
                .flatMap(team -> team.getPlayers().stream())
                .map(PlayerRef::create)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<PlayerRef> getWinningPlayers() {
        return players;
    }

    @Override
    public Set<TeamRef> getWinningSubjects() {
        return refs;
    }

    @Override
    public MiniGameResults getResults() {
        return results;
    }
}

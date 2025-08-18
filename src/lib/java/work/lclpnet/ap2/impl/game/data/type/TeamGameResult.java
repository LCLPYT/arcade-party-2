package work.lclpnet.ap2.impl.game.data.type;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.GenericGameResult;
import work.lclpnet.ap2.api.game.data.SubjectRefResolver;
import work.lclpnet.ap2.api.game.team.Team;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TeamGameResult implements GenericGameResult<TeamRef> {

    private final List<ObjectIntPair<PlayerRef>> playerResults;
    private final List<ObjectIntPair<TeamRef>> subjectResults;
    private final Set<PlayerRef> players;
    private final Set<TeamRef> refs;

    public TeamGameResult(DataContainer<Team, TeamRef> data, SubjectRefResolver<Team, TeamRef> refResolver) {
        var byRank = data.streamEntriesRanked().toList();

        this.subjectResults = byRank.stream()
                .flatMap(Collection::stream)
                .toList();

        this.playerResults = this.subjectResults.stream()
                .flatMap(teamRank -> {
                    Team team = refResolver.resolve(teamRank.key());

                    if (team == null) {
                        return Stream.empty();
                    }

                    return team.getPlayers().stream()
                            .map(PlayerRef::create)
                            .map(ref -> ObjectIntPair.of(ref, teamRank.rightInt()));
                })
                .toList();

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
    public List<ObjectIntPair<PlayerRef>> getPlayerResults() {
        return playerResults;
    }

    @Override
    public List<ObjectIntPair<TeamRef>> getSubjectResults() {
        return subjectResults;
    }
}

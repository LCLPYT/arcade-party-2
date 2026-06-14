package work.lclpnet.ap2.impl.game.data.type;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.GenericGameResult;
import work.lclpnet.ap2.api.game.data.SubjectRefResolver;
import work.lclpnet.ap2.api.game.team.Team;

import java.util.*;
import java.util.stream.Collectors;

public class TeamGameResult implements GenericGameResult<TeamRef> {

    private final List<ObjectIntPair<PlayerRef>> playerResults;
    private final List<ObjectIntPair<TeamRef>> subjectResults;
    private final Map<PlayerRef, TeamRef> playerTeams;
    private final Set<PlayerRef> players;
    private final Set<TeamRef> refs;

    public TeamGameResult(DataContainer<Team, TeamRef> data, SubjectRefResolver<Team, TeamRef> refResolver) {
        var byRank = data.streamEntriesRanked().toList();

        this.subjectResults = byRank.stream()
                .flatMap(Collection::stream)
                .toList();

        List<ObjectIntPair<PlayerRef>> playerResults = new ArrayList<>();
        Map<PlayerRef, TeamRef> playerTeams = new LinkedHashMap<>();

        for (ObjectIntPair<TeamRef> teamRank : this.subjectResults) {
            TeamRef teamRef = teamRank.key();
            Team team = refResolver.resolve(teamRef);

            if (team == null) {
                continue;
            }

            for (ServerPlayer player : team.getPlayers()) {
                PlayerRef ref = PlayerRef.create(player);
                playerResults.add(ObjectIntPair.of(ref, teamRank.rightInt()));
                playerTeams.put(ref, teamRef);
            }
        }

        this.playerResults = List.copyOf(playerResults);
        this.playerTeams = Collections.unmodifiableMap(playerTeams);

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
    public @NonNull Set<PlayerRef> getWinningPlayers() {
        return players;
    }

    @Override
    public @NonNull Set<TeamRef> getWinningSubjects() {
        return refs;
    }

    @Override
    public @NotNull List<@NotNull ObjectIntPair<@NotNull PlayerRef>> getPlayerResults() {
        return playerResults;
    }

    @Override
    public @NotNull List<@NotNull ObjectIntPair<TeamRef>> getSubjectResults() {
        return subjectResults;
    }

    public @NotNull Map<@NotNull PlayerRef, @NotNull TeamRef> getPlayerTeams() {
        return playerTeams;
    }
}

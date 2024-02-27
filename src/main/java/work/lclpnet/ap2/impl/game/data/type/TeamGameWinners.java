package work.lclpnet.ap2.impl.game.data.type;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.data.GameWinners;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.kibu.translate.TranslationService;

import java.util.Set;
import java.util.stream.Collectors;

public class TeamGameWinners implements GameWinners<TeamRef> {

    private final Set<ServerPlayerEntity> players;
    private final Set<TeamRef> refs;

    public TeamGameWinners(Set<Team> winners, TranslationService translations) {
        this.players = winners.stream()
                .flatMap(team -> team.getPlayers().stream())
                .collect(Collectors.toUnmodifiableSet());

        this.refs = winners.stream()
                .map(Team::getKey)
                .map(key -> new TeamRef(key, translations))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<ServerPlayerEntity> getPlayers() {
        return players;
    }

    @Override
    public Set<TeamRef> getSubjects() {
        return refs;
    }
}

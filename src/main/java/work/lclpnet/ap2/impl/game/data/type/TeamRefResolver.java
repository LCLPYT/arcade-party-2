package work.lclpnet.ap2.impl.game.data.type;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.SubjectRefResolver;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;

public class TeamRefResolver implements SubjectRefResolver<Team, TeamRef> {

    private final TeamManager teamManager;

    public TeamRefResolver(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public @Nullable Team resolve(TeamRef ref) {
        return teamManager.getTeam(ref.getKey()).orElse(null);
    }
}

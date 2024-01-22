package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;

import java.util.Map;
import java.util.Objects;

public class CozyCampfireBaseManager {

    private final Map<Team, CozyCampfireBase> bases;
    private final TeamManager teamManager;

    public CozyCampfireBaseManager(Map<Team, CozyCampfireBase> bases, TeamManager teamManager) {
        this.bases = bases;
        this.teamManager = teamManager;
    }

    private CozyCampfireBase requireBase(Team team) {
        return Objects.requireNonNull(bases.get(team), "Base not configured for team " + team.getKey().id());
    }

    public boolean isInAnyBase(double x, double y, double z) {
        return bases.values().stream().anyMatch(base -> base.isInside(x, y, z));
    }

    public boolean isInBase(ServerPlayerEntity player) {
        Team team = teamManager.getTeam(player).orElse(null);
        if (team == null) return false;

        CozyCampfireBase base = requireBase(team);

        return base.isInside(player.getX(), player.getY(), player.getZ());
    }

    public Map<Team, CozyCampfireBase> getBases() {
        return bases;
    }
}

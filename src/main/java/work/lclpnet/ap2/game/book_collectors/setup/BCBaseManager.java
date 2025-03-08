package work.lclpnet.ap2.game.book_collectors.setup;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamManager;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BCBaseManager {

    private final Map<Team, BCBase> bases;
    private final TeamManager teamManager;

    public BCBaseManager(Map<Team, BCBase> bases, TeamManager teamManager) {
        this.bases = bases;
        this.teamManager = teamManager;
    }

    private BCBase requireBase(Team team) {
        return Objects.requireNonNull(bases.get(team), "Base not configured for team " + team.getKey().id());
    }

    public boolean isInAnyBase(double x, double y, double z) {
        return bases.values().stream().anyMatch(base -> base.isInside(x, y, z));
    }

    public boolean isInBase(ServerPlayerEntity player) {
        Team team = teamManager.getTeam(player).orElse(null);
        if (team == null) return false;

        BCBase base = requireBase(team);

        return base.isInside(player.getX(), player.getY(), player.getZ());
    }

    public Optional<Team> blockPosInAnyBase(BlockPos pos) {
        return bases.entrySet().stream().filter(base -> base.getValue().isInside(pos.getX(), pos.getY(), pos.getZ())).map(Map.Entry::getKey).findFirst();
    }

    public Optional<BCBase> getBase(Team team) {
        return Optional.ofNullable(bases.get(team));
    }

    public Map<Team, BCBase> getBases() {
        return bases;
    }
}

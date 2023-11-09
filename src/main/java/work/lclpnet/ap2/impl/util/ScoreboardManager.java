package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import work.lclpnet.mplugins.ext.Unloadable;

import java.util.HashSet;
import java.util.Set;

public class ScoreboardManager implements Unloadable {

    private final ServerScoreboard scoreboard;
    private final Set<Team> teams = new HashSet<>();

    public ScoreboardManager(ServerScoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    public void joinTeam(Entity player, Team team) {
        scoreboard.addPlayerToTeam(player.getEntityName(), team);
    }

    public void joinTeam(Iterable<? extends Entity> players, Team team) {
        for (Entity entity : players) {
            joinTeam(entity, team);
        }
    }

    public Team createTeam(String name) {
        removeTeam(name);

        Team team = scoreboard.addTeam(name);

        synchronized (this) {
            teams.add(team);
        }

        return team;
    }

    private void removeTeam(String name) {
        Team team = scoreboard.getTeam(name);

        if (team == null) return;

        scoreboard.removeTeam(team);

        synchronized (this) {
            teams.remove(team);
        }
    }

    @Override
    public void unload() {
        synchronized (this) {
            teams.forEach(scoreboard::removeTeam);
            teams.clear();
        }
    }
}

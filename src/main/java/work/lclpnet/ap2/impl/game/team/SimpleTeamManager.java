package work.lclpnet.ap2.impl.game.team;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.team.*;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;

import java.util.*;

public class SimpleTeamManager implements TeamManager {

    private final PlayerManager playerManager;
    private final TeamConfig teamConfig;
    private final CustomScoreboardManager scoreboard;
    private final Map<TeamKey, Team> teams = new HashMap<>();
    private final Map<TeamKey, net.minecraft.scoreboard.Team> mcTeams = new HashMap<>();
    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private final Set<TeamKey> eliminated = new HashSet<>();
    @Nullable
    private TeamEliminatedListener listener = null;

    public SimpleTeamManager(PlayerManager playerManager, TeamConfig teamConfig, CustomScoreboardManager scoreboard) {
        this.playerManager = playerManager;
        this.teamConfig = teamConfig;
        this.scoreboard = scoreboard;
    }

    @Override
    public Set<Team> getTeams() {
        return new HashSet<>(teams.values());
    }

    @Override
    public Optional<net.minecraft.scoreboard.Team> getMinecraftTeam(TeamKey key) {
        return Optional.ofNullable(mcTeams.get(key));
    }

    @Override
    public Optional<Team> getTeam(TeamKey key) {
        return Optional.ofNullable(teams.get(key));
    }

    @Override
    public Optional<Team> getTeam(ServerPlayerEntity player) {
        return Optional.ofNullable(playerTeams.get(player.getUuid()));
    }

    @Override
    public synchronized void partitionIntoTeams(Set<ServerPlayerEntity> players, Set<TeamKey> keys) {
        synchronized (this) {
            reset();

            createTeams(keys);
        }

        // use pre-configured team constellations
        var mapping = teamConfig.getMapping();

        mapping.forEach((player, key) -> {
            Team team = getTeam(key).orElseThrow(() ->
                    new IllegalStateException("Team '%s' is not registered".formatted(key.id())));

            joinTeam(player, team);
        });

        // distribute the rest of the players
        Set<ServerPlayerEntity> notMapped = new HashSet<>(players);
        notMapped.removeAll(mapping.keySet());

        TeamPartitioner partitioner = teamConfig.getPartitioner();
        var partitions = partitioner.splitIntoTeams(notMapped, getTeams());
        partitions.forEach(this::joinTeam);
    }

    @Override
    public boolean isParticipating(TeamKey key) {
        return !eliminated.contains(key);
    }

    @Override
    public void setTeamEliminated(Team team) {
        if (!hasTeam(team.getKey())) return;

        if (eliminated.add(team.getKey()) && listener != null) {
            listener.teamEliminated(team);
        }
    }

    @Override
    public void bind(TeamEliminatedListener listener) {
        this.listener = listener;
    }

    private boolean hasTeam(TeamKey team) {
        return teams.containsKey(team);
    }

    private void createTeams(Set<TeamKey> keys) {
        for (TeamKey key : keys) {
            SimpleTeam team = createTeam(key);
            teams.put(key, team);
        }
    }

    @NotNull
    private SimpleTeam createTeam(TeamKey key) {
        if (hasTeamId(key.id())) {
            throw new IllegalStateException("Duplicate team id '%s'".formatted(key.id()));
        }

        var mcTeam = scoreboard.createTeam(key.id());
        mcTeam.setColor(key.colorFormat());

        mcTeams.put(key, mcTeam);

        return new SimpleTeam(key, playerManager);
    }

    private boolean hasTeamId(String id) {
        return teams.keySet().stream().anyMatch(key -> key.id().equals(id));
    }

    private void joinTeam(ServerPlayerEntity player, Team team) {
        synchronized (this) {
            team.addPlayer(player);
            playerTeams.put(player.getUuid(), team);

            var mcTeam = mcTeams.get(team.getKey());

            if (mcTeam != null) {
                scoreboard.joinTeam(player, mcTeam);
            }
        }
    }

    private void leaveTeam(ServerPlayerEntity player) {
        synchronized (this) {
            Team team = playerTeams.remove(player.getUuid());

            if (team == null) return;

            team.removePlayer(player);

            var mcTeam = mcTeams.get(team.getKey());

            if (mcTeam != null) {
                scoreboard.leaveTeam(player, mcTeam);
            }
        }
    }

    private void destroyMcTeam(TeamKey key) {
        scoreboard.removeTeam(key.id());

        mcTeams.remove(key);
    }

    private void reset() {
        teams.keySet().forEach(this::destroyMcTeam);
        teams.clear();
        playerTeams.clear();
        mcTeams.clear();
    }

    public void init(HookRegistrar hooks) {
        // move player back into the minecraft team, as they are automatically removed when quitting by the CustomScoreboardManager
        hooks.registerHook(PlayerConnectionHooks.JOIN, player -> {
            Team team = playerTeams.get(player.getUuid());

            if (team == null) return;

            var mcTeam = mcTeams.get(team.getKey());

            if (mcTeam != null) {
                scoreboard.joinTeam(player, mcTeam);
            }
        });
    }
}

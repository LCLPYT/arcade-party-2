package work.lclpnet.ap2.impl.game.team;

import lombok.Setter;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.team.*;
import work.lclpnet.ap2.core.hook.PlayerDisplayNameCallback;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;

import java.util.*;

public class SimpleTeamManager implements TeamManager {

    private final PlayerList playerManager;
    private final TeamConfig teamConfig;
    private final CustomScoreboardManager scoreboard;
    private final PlayerUtil playerUtil;
    private final Map<TeamKey, Team> teams = new HashMap<>();
    private final Map<TeamKey, PlayerTeam> mcTeams = new HashMap<>();
    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private final Set<TeamKey> eliminated = new HashSet<>();
    @Nullable
    private TeamEliminatedListener listener = null;
    @Setter
    private boolean useColorCodes = false;

    public SimpleTeamManager(PlayerList playerManager, TeamConfig teamConfig, CustomScoreboardManager scoreboard, PlayerUtil playerUtil) {
        this.playerManager = playerManager;
        this.teamConfig = teamConfig;
        this.scoreboard = scoreboard;
        this.playerUtil = playerUtil;
    }

    @Override
    public Set<Team> getTeams() {
        return new HashSet<>(teams.values());
    }

    @Override
    public Optional<PlayerTeam> getMinecraftTeam(TeamKey key) {
        return Optional.ofNullable(mcTeams.get(key));
    }

    @Override
    public Optional<Team> getTeam(TeamKey key) {
        return Optional.ofNullable(teams.get(key));
    }

    @Override
    public Optional<Team> getTeam(UUID uuid) {
        return Optional.ofNullable(playerTeams.get(uuid));
    }

    @Override
    public synchronized void partitionIntoTeams(Set<ServerPlayer> players, Set<? extends TeamKey> keys) {
        synchronized (this) {
            reset();

            for (TeamKey key : keys) {
                registerTeam(key);
            }
        }

        // use pre-configured team constellations
        var mapping = teamConfig.getMapping();

        mapping.forEach((player, key) -> {
            Team team = getTeam(key).orElseThrow(() ->
                    new IllegalStateException("Team '%s' is not registered".formatted(key.id())));

            joinTeam(player, team);
        });

        // distribute the rest of the players
        Set<ServerPlayer> notMapped = new HashSet<>(players);
        notMapped.removeAll(mapping.keySet());

        TeamPartitioner partitioner = teamConfig.getPartitioner();
        var partitions = partitioner.splitIntoTeams(notMapped, getTeams());
        partitions.forEach(this::joinTeam);

        playerUtil.updatePlayerListNames(players);
    }

    @Override
    public boolean isParticipating(TeamKey key) {
        return !eliminated.contains(key);
    }

    @Override
    public void setTeamEliminated(Team team) {
        if (!hasTeam(team.key())) return;

        if (eliminated.add(team.key()) && listener != null) {
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

    @Override
    public synchronized Team registerTeam(TeamKey key) {
        SimpleTeam team = createTeam(key);
        teams.put(key, team);
        return team;
    }

    @NotNull
    private SimpleTeam createTeam(TeamKey key) {
        if (hasTeamId(key.id())) {
            throw new IllegalStateException("Duplicate team id '%s'".formatted(key.id()));
        }

        var mcTeam = scoreboard.createTeam(key.id());

        if (useColorCodes) {
            mcTeam.setColor(key.formatting());
        }

        mcTeams.put(key, mcTeam);

        return new SimpleTeam(key, playerManager);
    }

    private boolean hasTeamId(String id) {
        return teams.keySet().stream().anyMatch(key -> key.id().equals(id));
    }

    @Override
    public void joinTeam(ServerPlayer player, Team team) {
        synchronized (this) {
            team.addPlayer(player);
            playerTeams.put(player.getUUID(), team);

            var mcTeam = mcTeams.get(team.key());

            if (mcTeam != null) {
                scoreboard.joinTeam(player, mcTeam);
            }
        }
    }

    private void leaveTeam(ServerPlayer player) {
        synchronized (this) {
            Team team = playerTeams.remove(player.getUUID());

            if (team == null) return;

            team.removePlayer(player);

            var mcTeam = mcTeams.get(team.key());

            if (mcTeam != null) {
                scoreboard.leaveTeam(player, mcTeam);
            }
        }
    }

    private void destroyMcTeam(TeamKey key) {
        scoreboard.removeTeam(key.id());

        synchronized (this) {
            mcTeams.remove(key);
        }
    }

    private synchronized void reset() {
        teams.keySet().forEach(this::destroyMcTeam);
        teams.clear();
        playerTeams.clear();
        mcTeams.clear();
    }

    public void init(HookRegistrar hooks) {
        // move player back into the minecraft team, as they are automatically removed when quitting by the CustomScoreboardManager
        PlayerConnectionHooks.JOIN.registerWith(hooks, player -> {
            PlayerTeam mcTeam;

            synchronized (this) {
                Team team = playerTeams.get(player.getUUID());

                if (team == null) return;

                mcTeam = mcTeams.get(team.key());
            }

            if (mcTeam != null) {
                scoreboard.joinTeam(player, mcTeam);
            }
        });

        PlayerDisplayNameCallback.HOOK.registerWith(hooks, (player, name) -> {
            Team team;

            synchronized (this) {
                team = playerTeams.get(player.getUUID());

                if (team == null) return name;
            }

            MutableComponent text = name instanceof MutableComponent ? (MutableComponent) name : name.copy();

            return text.withStyle(style -> style.withColor(team.key().color()));
        });
    }
}

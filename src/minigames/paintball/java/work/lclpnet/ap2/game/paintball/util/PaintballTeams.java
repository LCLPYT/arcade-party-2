package work.lclpnet.ap2.game.paintball.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.gaco.core.api.Partial;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.ap2.impl.game.team.ApTeams;
import work.lclpnet.ap2.impl.util.StreamUtil;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.stream.Stream;

public class PaintballTeams implements Iterable<PaintballTeam> {

    @Getter
    private final TeamManager teamManager;
    private final GameMap map;
    private final Participants participants;
    private final Random random;
    private final Logger logger;
    private final Map<TeamKey, PaintballTeam> teamsByKey = new HashMap<>();
    private final Object2IntMap<PaintballTeam> teamGroups = new Object2IntOpenHashMap<>();
    private List<PaintballTeam> teams = null;

    public PaintballTeams(TeamManager teamManager, GameMap map, Participants participants, Random random, Logger logger) {
        this.teamManager = teamManager;
        this.map = map;
        this.participants = participants;
        this.random = random;
        this.logger = logger;
    }

    public void setup() {
        this.teams = setupTeams();

        teamGroups.clear();
        int group = 0x2;

        for (PaintballTeam team : teams) {
            teamsByKey.put(team.key(), team);
            teamGroups.put(team, group);

            group <<= 2;
        }
    }

    private List<PaintballTeam> setupTeams() {
        JSONObject props = map.getProperties();
        JSONArray array = props.getJSONArray("teams");
        List<Partial<PaintballTeam, DyeTeamKey>> partial = new ArrayList<>(array.length());

        for (Object entry : array) {
            if (!(entry instanceof JSONObject json)) {
                logger.error("Invalid team entry: {}", entry);
                continue;
            }

            partial.add(PaintballTeam.fromJson(json));
        }

        // choose random team colors, by first choosing a random base color and then choosing according complementary colors
        IndexedSet<DyeTeamKey> colorPool = availableTeamColors(props);
        DyeTeamKey base = colorPool.get(random.nextInt(colorPool.size()));
        List<DyeTeamKey> complementary = ApTeams.complementary(base, colorPool, partial.size());

        return StreamUtil.zip(partial.stream(), complementary.stream(), Partial::with).toList();
    }

    private @NotNull IndexedSet<DyeTeamKey> availableTeamColors(JSONObject props) {
        JSONArray array = props.getJSONArray("available-team-colors");

        IndexedSet<DyeTeamKey> teamPool = new IndexedSet<>();

        for (Object item : array) {
            if (!(item instanceof String id)) {
                logger.warn("Expected team id of type string, but got: {}", item);
                continue;
            }

            DyeTeamKey key = DyeTeamKey.byId(id);

            if (key == null) {
                logger.warn("Unknown team color \"{}\"", id);
                continue;
            }

            teamPool.add(key);
        }

        return teamPool;
    }

    public boolean isMember(PaintballTeam pbt, ServerPlayerEntity player) {
        return teamOf(player)
                .map(team -> team == pbt)
                .orElse(false);
    }

    public Optional<PaintballTeam> teamOf(ServerPlayerEntity player) {
        return teamManager.getTeam(player)
                .map(Team::key)
                .map(teamsByKey::get);
    }

    public Optional<PaintballTeam> teamBaseAt(BlockPos pos) {
        for (PaintballTeam team : teams) {
            if (team.baseBounds().contains(pos)) {
                return Optional.of(team);
            }
        }

        return Optional.empty();
    }

    public int playerGroup(PaintballTeam team) {
        return teamGroups.getOrDefault(team, 0x1);
    }

    public int bulletGroup(ServerPlayerEntity player) {
        PaintballTeam team = teamOf(player).orElse(null);

        if (team == null) {
            return 0x1;
        }

        int group = playerGroup(team);

        return group == 0 || group == 1 ? group : bulletGroup(group);
    }

    public int bulletGroup(int group) {
        return group << 1;
    }

    public int bulletCollisionFlags(ServerPlayerEntity player) {
        PaintballTeam ownTeam = teamOf(player).orElse(null);

        if (ownTeam == null) {
            return 0x1;
        }

        // bullets may collide with everything except the own player group
        int flags = 0x1;

        for (var entry : teamGroups.object2IntEntrySet()) {
            int playerGroup = entry.getIntValue();

            flags |= bulletGroup(playerGroup);

            if (entry.getKey() != ownTeam) {
                flags |= playerGroup;
            }
        }

        return flags;
    }

    public int playerDeficit(PaintballTeam pbt) {
        Team team = teamManager.getTeam(pbt).orElse(null);

        if (team == null) return 0;

        final int maxPlayerCount = teamManager.getTeams().stream()
                .mapToInt(t -> t.getParticipatingPlayers(participants).size())
                .max().orElse(0);

        return maxPlayerCount - team.getParticipatingPlayers(participants).size();
    }

    @Override
    public @NotNull Iterator<PaintballTeam> iterator() {
        return teams.iterator();
    }

    public Stream<PaintballTeam> stream() {
        return teams.stream();
    }
}

package work.lclpnet.ap2.game.paintball.util;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.ds.Partial;
import work.lclpnet.ap2.api.game.team.DyeTeamKey;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.api.game.team.TeamManager;
import work.lclpnet.ap2.impl.ds.IndexedSet;
import work.lclpnet.ap2.impl.game.team.ApTeams;
import work.lclpnet.ap2.impl.util.StreamUtil;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.stream.Stream;

public class PaintballTeams implements Iterable<PaintballTeam> {

    private final TeamManager teamManager;
    private final GameMap map;
    private final Random random;
    private final Logger logger;
    private final Map<TeamKey, PaintballTeam> teamsByKey = new HashMap<>();
    private List<PaintballTeam> teams = null;

    public PaintballTeams(TeamManager teamManager, GameMap map, Random random, Logger logger) {
        this.teamManager = teamManager;
        this.map = map;
        this.random = random;
        this.logger = logger;
    }

    public void setup() {
        this.teams = setupTeams();

        for (PaintballTeam team : teams) {
            teamsByKey.put(team.key(), team);
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
                .map(Team::getKey)
                .map(teamsByKey::get);
    }

    @Override
    public @NotNull Iterator<PaintballTeam> iterator() {
        return teams.iterator();
    }

    public Stream<PaintballTeam> stream() {
        return teams.stream();
    }
}

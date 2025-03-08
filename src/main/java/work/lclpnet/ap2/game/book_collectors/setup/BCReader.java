package work.lclpnet.ap2.game.book_collectors.setup;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class BCReader {

    private final GameMap map;
    private final Logger logger;

    public BCReader(GameMap map, Logger logger) {
        this.map = map;
        this.logger = logger;
    }

    public CompletableFuture<Map<Team, BCBase>> readBases(Set<Team> teams) {
        return supplyAsync(() -> _readBases(teams));
    }

    public Map<Team, BCBase> _readBases(Set<Team> teams) {

        JSONObject basesJson = map.requireProperty("bases");

        Map<Team, BCBase> bases = new HashMap<>();

        for (Team team : teams) {
            String id = team.getKey().id();
            Identifier mapId = map.getDescriptor().getIdentifier();

            if (!basesJson.has(id)) {
                logger.error("No base configured for team {} in map {}", id, mapId);
                continue;
            }

            JSONObject json = basesJson.getJSONObject(id);

            BCBase base = readBase(json, id, mapId);

            if (base == null) continue;

            bases.put(team, base);
        }

        return bases;
    }

    @Nullable
    private BCBase readBase(JSONObject json, String teamId, Identifier mapId) {
        if (!json.has("bounds")) {
            logger.error("Base of team {} in map {} does not contain property 'bounds'", teamId, mapId);
            return null;
        }

        JSONArray boundsArray = json.getJSONArray("bounds");
        List<BlockBox> bounds = new ArrayList<>(boundsArray.length());

        for (Object entry : boundsArray) {
            if (!(entry instanceof JSONArray array)) continue;

            BlockBox box = MapUtil.readBox(array);
            bounds.add(box);
        }

        return new BCBase(bounds);
    }
}

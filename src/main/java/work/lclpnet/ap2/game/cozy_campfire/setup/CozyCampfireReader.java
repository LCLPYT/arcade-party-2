package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;

public class CozyCampfireReader {

    private final GameMap map;
    private final Logger logger;

    public CozyCampfireReader(GameMap map, Logger logger) {
        this.map = map;
        this.logger = logger;
    }

    public Map<Team, CozyCampfireBase> readBases(Set<Team> teams) {
        JSONObject basesJson = map.requireProperty("bases");

        Map<Team, CozyCampfireBase> bases = new HashMap<>();

        for (Team team : teams) {
            String id = team.getKey().id();
            Identifier mapId = map.getDescriptor().getIdentifier();

            if (!basesJson.has(id)) {
                logger.error("No base configured for team {} in map {}", id, mapId);
                continue;
            }

            JSONObject json = basesJson.getJSONObject(id);

            CozyCampfireBase base = readBase(json, id, mapId);

            if (base == null) continue;

            bases.put(team, base);
        }

        return bases;
    }

    @Nullable
    private CozyCampfireBase readBase(JSONObject json, String teamId, Identifier mapId) {
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

        if (!json.has("campfire")) {
            logger.error("Base of team {} in map {} does not contain property 'campfire'", teamId, mapId);
            return null;
        }

        BlockPos campfirePos = MapUtil.readBlockPos(json.getJSONArray("campfire"));

        if (!json.has("entity")) {
            logger.error("Base of team {} in map {} does not contain property 'entity'", teamId, mapId);
            return null;
        }

        UUID entityUuid = UUID.fromString(json.getString("entity"));

        return new CozyCampfireBase(bounds, campfirePos, entityUuid);
    }
}

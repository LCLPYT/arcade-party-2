package work.lclpnet.ap2.game.cozy_campfire.setup;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class CCReader {

    private final GameMap map;
    private final ServerWorld world;
    private final Logger logger;

    public CCReader(GameMap map, ServerWorld world, Logger logger) {
        this.map = map;
        this.world = world;
        this.logger = logger;
    }

    public CompletableFuture<Map<Team, CCBase>> readBases(Set<Team> teams) {
        return supplyAsync(() -> _readBases(teams));
    }

    public Map<Team, CCBase> _readBases(Set<Team> teams) {
        var session = ((MinecraftServerAccessor) world.getServer()).getSession();
        Path storage = session.getWorldDirectory(world.getRegistryKey());
        Path schematicsDir = storage.resolve("schematics");

        JSONObject basesJson = map.requireProperty("bases");

        Map<Team, CCBase> bases = new HashMap<>();

        for (Team team : teams) {
            String id = team.key().id();
            Identifier mapId = map.getDescriptor().getIdentifier();

            if (!basesJson.has(id)) {
                logger.error("No base configured for team {} in map {}", id, mapId);
                continue;
            }

            JSONObject json = basesJson.getJSONObject(id);

            CCBase base = readBase(json, id, mapId, schematicsDir);

            if (base == null) continue;

            bases.put(team, base);
        }

        return bases;
    }

    @Nullable
    private CCBase readBase(JSONObject json, String teamId, Identifier mapId, Path schematicsDir) {
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

        JSONArray doorPosTuple = json.optJSONArray("door-pos");
        BlockPos doorPos = doorPosTuple != null ? MapUtil.readBlockPos(doorPosTuple) : null;

        BlockStructure doorSchem = readDoor(json, schematicsDir);

        return new CCBase(bounds, campfirePos, entityUuid, doorSchem, doorPos);
    }

    @Nullable
    private BlockStructure readDoor(JSONObject json, Path schematicsDir) {
        String name = json.optString("door-schematic");

        if (name == null) {
            return null;
        }

        Path path = schematicsDir.resolve(name + ".schem");

        try (var in = Files.newInputStream(path)) {
            return SchematicFormats.SPONGE_V2.reader().read(in, FabricBlockStateAdapter.getInstance());
        } catch (IOException e) {
            logger.error("Failed to read schematic {}", path, e);
            return null;
        }
    }
}

package work.lclpnet.ap2.game.book_collectors.setup;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.team.Team;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
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

public class BCReader {

    private final GameMap map;
    private final Logger logger;
    private final ServerWorld world;

    public BCReader(GameMap map, ServerWorld world, Logger logger) {
        this.map = map;
        this.world = world;
        this.logger = logger;
    }

    public CompletableFuture<Map<Team, BCBase>> readBases(Set<Team> teams) {
        return supplyAsync(() -> _readBases(teams));
    }

    public Map<Team, BCBase> _readBases(Set<Team> teams) {
        var session = ((MinecraftServerAccessor) world.getServer()).getSession();
        Path storage = session.getWorldDirectory(world.getRegistryKey());
        Path schematicsDir = storage.resolve("schematics");

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

            BCBase base = readBase(json, id, mapId, schematicsDir);

            if (base == null) continue;

            bases.put(team, base);
        }

        return bases;
    }

    @Nullable
    private BCBase readBase(JSONObject json, String teamId, Identifier mapId, Path schematicsDir) {
        if (!json.has("bounds")) {
            logger.error("Base of team {} in map {} does not contain property 'bounds'", teamId, mapId);
            return null;
        }

        JSONObject boundsObject = json.getJSONObject("bounds");

        BlockShape box = MapUtil.readShape(boundsObject);

        List<BlockShape> bounds = new ArrayList<>();
        bounds.add(box);

        JSONArray doorPosTuple = json.optJSONArray("door-pos");
        BlockPos doorPos = doorPosTuple != null ? MapUtil.readBlockPos(doorPosTuple) : null;

        BlockStructure doorSchematic = readDoor(json, schematicsDir);

        return new BCBase(bounds, doorSchematic, doorPos);
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

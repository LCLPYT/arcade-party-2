package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.ap2.impl.util.effect.ApEffects;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.max;

public class JumpAndRunSetup {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final Logger logger;
    private final JumpAndRunGenerator generator;
    private final JumpAndRunPlacer placer;

    public JumpAndRunSetup(MiniGameHandle gameHandle, GameMap map, ServerWorld world, float targetMinutes) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
        this.logger = gameHandle.getLogger();
        this.generator = new JumpAndRunGenerator(targetMinutes, new Random(), logger);
        this.placer = new JumpAndRunPlacer(world);
    }

    public CompletableFuture<JumpAndRun> setup() {
        return readParts().thenCompose(this::generate);
    }

    private CompletableFuture<JumpAndRun> generate(Parts parts) {
        BlockPos spawnPos = BlockPos.ofFloored(MapUtils.getSpawnPosition(map));
        JumpAndRun jumpAndRun = generator.generate(parts, spawnPos);

        return world.getServer().submit(() -> {
            placer.place(jumpAndRun);
            return jumpAndRun;
        });
    }

    private CompletableFuture<Parts> readParts() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return this.readPartsSync();
            } catch (IOException e) {
                throw new RuntimeException("Failed to read maps", e);
            }
        });
    }

    private Parts readPartsSync() throws IOException {
        var session = ((MinecraftServerAccessor) gameHandle.getServer()).getSession();
        Path storage = session.getWorldDirectory(world.getRegistryKey());

        Path schematicsDir = storage.resolve("schematics");

        List<JumpRoom> jumpRooms = readJumpRooms(schematicsDir);

        String startId = map.requireProperty("start-room");
        String endId = map.requireProperty("end-room");

        BlockStructure startStruct = readStructure(startId, schematicsDir).orElseThrow();
        BlockStructure endStruct = readStructure(endId, schematicsDir).orElseThrow();

        JumpEnd start = JumpEnd.from(startStruct);
        JumpEnd end = JumpEnd.from(endStruct);

        return new Parts(Collections.unmodifiableList(jumpRooms), start, end);
    }

    @NotNull
    private List<JumpRoom> readJumpRooms(Path schematicsDir) {
        JSONArray rooms = map.requireProperty("rooms");

        List<JumpRoom> jumpRooms = new ArrayList<>();

        for (Object item : rooms) {
            if (!(item instanceof JSONObject json)) {
                logger.warn("Invalid rooms entry: {}", item);
                continue;
            }

            String id = json.getString("id");
            float value = json.getNumber("value").floatValue();

            JumpAssistance assistance;

            if (json.has("assist")) {
                JSONArray array = json.getJSONArray("assist");
                assistance = JumpAssistance.fromJson(array, logger);
            } else {
                assistance = JumpAssistance.EMPTY;
            }

            List<Checkpoint> checkpoints;

            if (json.has("checkpoints")) {
                JSONArray array = json.getJSONArray("checkpoints");
                checkpoints = readCheckpoints(array);
            } else {
                checkpoints = List.of();
            }

            JumpRoom.Start start;

            if (json.has("start")) {
                JSONObject startJson = json.getJSONObject("start");
                BlockPos spawn = MapUtil.readBlockPos(startJson.getJSONArray("spawn"));
                float yaw = MapUtil.readAngle(startJson.getNumber("yaw"));
                BlockBox bounds;
                List<BlockBox> gateBoxes;


                /*
                gateArray:   [ [x1, y1, z1], [x2, y2, z2] ]
                OR
                gateArray: [
                             [ [x01, y01, z01], [x02, y02, z02] ],
                             [ [x11, y11, z11], [x12, y12, z12] ],
                             ...
                           ]
                 */

                JSONArray gateArray = startJson.getJSONArray("gate");

                if (gateArray.getJSONArray(0).get(0) instanceof JSONArray) {
                    // gate consists out of multiple boxes
                    bounds = MapUtil.readBox(gateArray.getJSONArray(0));

                    gateBoxes = new ArrayList<>(gateArray.length());
                    gateBoxes.add(bounds);

                    for (int i = 1; i < gateArray.length(); i++) {
                        gateBoxes.add(MapUtil.readBox(gateArray.getJSONArray(i)));
                    }
                } else {
                    // assume single gate box
                    bounds = MapUtil.readBox(gateArray);
                    gateBoxes = List.of(bounds);
                }

                start = new JumpRoom.Start(spawn, yaw, bounds, gateBoxes);
            } else {
                start = null;
            }

            Checkpoint end;

            if (json.has("end")) {
                end = Checkpoint.fromJson(json.getJSONObject("end"));
            } else {
                end = null;
            }

            int stackingMargin = max(0, json.optNumber("stacking-margin", 0).intValue());
            float weight = max(0, json.optNumber("weight", 1).floatValue());

            Set<ApEffect> effects;

            if (json.has("effects")) {
                effects = ApEffects.fromJson(json.getJSONArray("effects"), logger);
            } else {
                effects = Set.of();
            }

            var metaData = new JumpRoom.MetaData(value, stackingMargin, weight, effects);

            readRoom(id, schematicsDir)
                    .map(partial -> partial.with(metaData, assistance, checkpoints, start, end))
                    .ifPresent(jumpRooms::add);
        }

        return jumpRooms;
    }

    @NotNull
    private List<Checkpoint> readCheckpoints(JSONArray array) {
        List<Checkpoint> checkpoints = new ArrayList<>(array.length());

        for (Object entry : array) {
            if (!(entry instanceof JSONObject obj)) {
                logger.warn("Invalid array entry {}", entry);
                continue;
            }

            Checkpoint checkpoint = Checkpoint.fromJson(obj);
            checkpoints.add(checkpoint);
        }

        return checkpoints;
    }

    private Optional<JumpRoom.Partial> readRoom(String id, Path schematicsDir) {
        return readStructure(id, schematicsDir).map(structure -> {
            try {
                return JumpRoom.Partial.from(structure, id);
            } catch (Throwable t) {
                logger.error("Invalid room schematic '{}'", id, t);
                return null;
            }
        });
    }

    private Optional<BlockStructure> readStructure(String id, Path schematicsDir) {
        Path path = schematicsDir.resolve(id.concat(".schem"));

        return StructureUtil.readAndFixStructure(path, logger, world.getRegistryManager());
    }

    public record Parts(List<JumpRoom> rooms, JumpEnd start, JumpEnd end) {}
}

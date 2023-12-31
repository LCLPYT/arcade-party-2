package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.schematic.api.SchematicReader;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class JumpAndRunSetup {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final Logger logger;
    private final JumpAndRunGenerator generator;
    private final JumpAndRunPlacer placer;

    public JumpAndRunSetup(MiniGameHandle gameHandle, GameMap map, ServerWorld world) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
        this.logger = gameHandle.getLogger();
        this.generator = new JumpAndRunGenerator(4.5f, new Random(), logger);  // time in minutes
        this.placer = new JumpAndRunPlacer(world);
    }

    public CompletableFuture<JumpAndRun> setup() {
        return readParts().thenApply(this::generate);
    }

    private JumpAndRun generate(Parts parts) {
        BlockPos spawnPos = BlockPos.ofFloored(MapUtils.getSpawnPosition(map));
        JumpAndRun jumpAndRun = generator.generate(parts, spawnPos);

        placer.place(jumpAndRun);

        return jumpAndRun;
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

        Set<JumpRoom> jumpRooms = readJumpRooms(schematicsDir);

        String startId = map.requireProperty("start-room");
        String endId = map.requireProperty("end-room");

        BlockStructure startStruct = readStructure(startId, schematicsDir).orElseThrow();
        BlockStructure endStruct = readStructure(endId, schematicsDir).orElseThrow();

        JumpEnd start = JumpEnd.from(startStruct);
        JumpEnd end = JumpEnd.from(endStruct);

        return new Parts(Collections.unmodifiableSet(jumpRooms), start, end);
    }

    @NotNull
    private Set<JumpRoom> readJumpRooms(Path schematicsDir) {
        JSONArray rooms = map.requireProperty("rooms");

        Set<JumpRoom> jumpRooms = new HashSet<>();

        for (Object item : rooms) {
            if (!(item instanceof JSONObject json)) {
                logger.warn("Invalid rooms entry: {}", item);
                continue;
            }

            String id = json.getString("id");
            float value = json.getNumber("value").floatValue();

            readRoom(id, value, schematicsDir).ifPresent(jumpRooms::add);
        }
        return jumpRooms;
    }

    private Optional<JumpRoom> readRoom(String id, float value, Path schematicsDir) {
        return readStructure(id, schematicsDir).map(structure -> {
            try {
                return JumpRoom.from(structure).with(value);
            } catch (Throwable t) {
                logger.error("Invalid room schematic '{}'", id, t);
                return null;
            }
        });
    }

    private Optional<BlockStructure> readStructure(String id, Path schematicsDir) {
        Path path = schematicsDir.resolve(id.concat(".schem"));
        SchematicReader reader = SchematicFormats.SPONGE_V2.reader();
        var adapter = FabricBlockStateAdapter.getInstance();

        try (var in = Files.newInputStream(path)) {
            BlockStructure structure = reader.read(in, adapter);
            return Optional.of(structure);
        } catch (IOException e) {
            logger.error("Failed to read schematic {}", path, e);
            return Optional.empty();
        }
    }

    public record Parts(Set<JumpRoom> rooms, JumpEnd start, JumpEnd end) {}
}

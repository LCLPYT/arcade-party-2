package work.lclpnet.ap2.game.jump_and_run.gen;

import net.minecraft.server.world.ServerWorld;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.map.schema.MapSchemaLoader;
import work.lclpnet.ap2.impl.util.structure.StructureUtil;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class JumpAndRunSetup {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final Logger logger;
    private final JumpAndRunGenerator generator;

    public JumpAndRunSetup(MiniGameHandle gameHandle, GameMap map, ServerWorld world, float targetMinutes) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
        this.logger = gameHandle.getLogger();
        this.generator = new JumpAndRunGenerator(gameHandle.getGameInfo(), targetMinutes, new Random(), gameHandle.getLogger());
    }

    public CompletableFuture<JumpAndRun> setup() {
        return readParts().thenCompose(this::generate);
    }

    private CompletableFuture<JumpAndRun> generate(Parts parts) {
        var modules = generator.generate(parts);

        if (modules.isEmpty()) {
            return CompletableFuture.failedFuture(new NoSuchElementException("No modules generated"));
        }

        MapSchemaLoader schemaLoader = new MapSchemaLoader(logger);

        var jnr = new JumpAndRun(map, gameHandle.getSubWorldManager(), modules, schemaLoader, gameHandle.getServer(),
                parts, generator);

        return jnr.loadModule().thenApply(res -> jnr);
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

        List<JumpModule> modules = getModules();

        BlockStructure startStruct = readStructure("jnr_start", schematicsDir).orElseThrow();
        BlockStructure endStruct = readStructure("jnr_end", schematicsDir).orElseThrow();

        JumpEnd start = JumpEnd.from(startStruct);
        JumpEnd end = JumpEnd.from(endStruct);

        return new Parts(modules, start, end);
    }

    private List<JumpModule> getModules() {
        JSONArray modulesJson = map.requireProperty("modules");
        List<JumpModule> modules = new ArrayList<>();

        for (Object o : modulesJson) {
            if (!(o instanceof JSONObject json)) {
                logger.warn("Invalid modules array entry: {}", o);
                continue;
            }

            var module = JumpModule.fromJson(json, logger);

            if (module == null) continue;

            modules.add(module);
        }

        return Collections.unmodifiableList(modules);
    }

    private Optional<BlockStructure> readStructure(String id, Path schematicsDir) {
        Path path = schematicsDir.resolve(id.concat(".schem"));

        return StructureUtil.readAndFixStructure(path, logger, world.getRegistryManager());
    }

    public record Parts(List<JumpModule> modules, JumpEnd start, JumpEnd end) {}
}

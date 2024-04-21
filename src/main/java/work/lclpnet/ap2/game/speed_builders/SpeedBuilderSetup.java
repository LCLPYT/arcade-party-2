package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.game.speed_builders.data.SbIslandData;
import work.lclpnet.ap2.game.speed_builders.data.SbIslandProto;
import work.lclpnet.ap2.game.speed_builders.data.SbModule;
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator;
import work.lclpnet.kibu.mc.BlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.schematic.api.SchematicReader;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SpeedBuilderSetup {

    private static final double ISLAND_SPACING = 10;
    private static final int SPAWN_Y = 64;
    private final Random random;
    private final Logger logger;
    private List<SbModule> modules = null;
    private List<SbIslandProto> islandProtos = null;

    public SpeedBuilderSetup(Random random, Logger logger) {
        this.random = random;
        this.logger = logger;
    }

    public CompletableFuture<Void> setup(GameMap map, ServerWorld world) {
        return loadAvailableIslands(map, world)
                .thenAccept(islands -> this.islandProtos = List.copyOf(islands))
                .thenComposeAsync(islands -> loadModules(world))
                .thenAccept(modules -> this.modules = List.copyOf(modules));
    }

    public Map<UUID, SbIsland> createIslands(Participants participants, ServerWorld world) {
        Objects.requireNonNull(islandProtos, "Island prototypes must be loaded");

        if (islandProtos.isEmpty()) {
            throw new IllegalStateException("No island prototypes available");
        }

        int count = participants.count();

        List<SbIslandData> islandData = new ArrayList<>(count);
        List<BlockStructure> structures = new ArrayList<>(count);
        List<UUID> players = new ArrayList<>(count);

        for (ServerPlayerEntity player : participants) {
            players.add(player.getUuid());

            SbIslandProto island = islandProtos.get(random.nextInt(islandProtos.size()));
            islandData.add(island.data());
            structures.add(island.structure());
        }

        Map<UUID, SbIsland> islandMapping = new HashMap<>(count);

        CircleStructureGenerator.placeStructures(structures, world, ISLAND_SPACING, (i, structure, circleOffset) -> {
            SbIslandData data = islandData.get(i);

            var absSpawn = data.spawn();
            var kibuOrigin = structure.getOrigin();
            BlockPos origin = new BlockPos(kibuOrigin.getX(), kibuOrigin.getY(), kibuOrigin.getZ());
            BlockPos spawn = absSpawn.subtract(origin);

            int x = circleOffset.x();
            int y = SPAWN_Y - spawn.getY();
            int z = circleOffset.z();

            BlockPos pos = new BlockPos(x, y, z);

            SbIsland island = new SbIsland(data, origin, pos);
            UUID uuid = players.get(i);

            islandMapping.put(uuid, island);

            return pos;
        });

        return islandMapping;
    }

    private CompletableFuture<Set<SbIslandProto>> loadAvailableIslands(GameMap map, ServerWorld world) {
        JSONArray islandsArray = map.requireProperty("islands");

        return CompletableFuture.supplyAsync(() -> {
            Path dir = getWorldDirectory(world).resolve("schematics").resolve("island");

            if (!Files.isDirectory(dir)) {
                logger.error("Directory {} does not exist", dir);
                return Set.of();
            }

            Set<SbIslandProto> islands = new HashSet<>(islandsArray.length());

            for (Object obj : islandsArray) {
                if (!(obj instanceof JSONObject jsonObj)) {
                    logger.warn("Invalid islands array element, skipping it...");
                    continue;
                }

                SbIslandData data;

                try {
                    data = SbIslandData.fromJson(jsonObj);
                } catch (JSONException e) {
                    logger.warn("Failed to read island from json", e);
                    continue;
                }

                Path path = dir.resolve(data.id() + ".schem");
                BlockStructure structure = loadSchematic(path);

                if (structure == null) continue;

                islands.add(new SbIslandProto(data, structure));
            }

            return islands;
        });
    }

    private CompletableFuture<Set<SbModule>> loadModules(ServerWorld world) {
        Path dir = getWorldDirectory(world).resolve("schematics").resolve("module");

        return CompletableFuture.supplyAsync(() -> {
            try (var files = Files.list(dir)) {
                return files
                        .filter(path -> path.getFileName().toString().endsWith(".schem"))
                        .filter(Files::isRegularFile)
                        .map(this::loadModule)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                logger.error("Failed to read module directory", e);
                return Set.of();
            }
        });
    }

    @Nullable
    private SbModule loadModule(Path path) {
        BlockStructure structure = loadSchematic(path);

        if (structure == null) return null;

        return new SbModule(structure);
    }

    @Nullable
    private BlockStructure loadSchematic(Path path) {
        SchematicReader schematicReader = SchematicFormats.SPONGE_V2.reader();
        BlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();

        try (var in = Files.newInputStream(path)) {
            return schematicReader.read(in, adapter);
        } catch (IOException e) {
            logger.error("Failed to load schematic {}", path, e);
            return null;
        }
    }

    private Path getWorldDirectory(ServerWorld world) {
        var session = ((MinecraftServerAccessor) world.getServer()).getSession();

        return session.getWorldDirectory(world.getRegistryKey());
    }
}

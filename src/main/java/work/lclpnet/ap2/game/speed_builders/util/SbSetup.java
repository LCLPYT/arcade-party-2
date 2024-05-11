package work.lclpnet.ap2.game.speed_builders.util;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.speed_builders.data.*;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.math.Vec2i;
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator;
import work.lclpnet.kibu.mc.BlockStateAdapter;
import work.lclpnet.kibu.mc.KibuBlockPos;
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

import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

public class SbSetup {

    private static final double ISLAND_SPACING = 2.0;
    private static final int SPAWN_Y = 64;
    private final Random random;
    private final Logger logger;
    private List<SbModule> modules = null;
    private List<SbIslandProto> islandProtos = null;
    private CenterIsland centerIsland = null;
    private UUID aelosId = null;

    public SbSetup(Random random, Logger logger) {
        this.random = random;
        this.logger = logger;
    }

    public CompletableFuture<Void> setup(GameMap map, ServerWorld world) {
        return loadAvailableIslands(map, world)
                .thenApply(islands -> this.islandProtos = List.copyOf(islands))
                .thenComposeAsync(islands -> loadModules(world, buildAreaDimensions(islands)))
                .thenAccept(modules -> this.modules = List.copyOf(modules))
                .thenComposeAsync(nil -> loadCenterIsland(map, world))
                .thenAccept(centerIsland -> this.centerIsland = centerIsland);
    }

    private Vec3i buildAreaDimensions(List<SbIslandProto> islands) {
        if (islands.isEmpty()) return Vec3i.ZERO;

        SbIslandProto proto = islands.getFirst();
        BlockBox buildArea = proto.data().buildArea();

        return new Vec3i(buildArea.getWidth(), buildArea.getHeight(), buildArea.getLength());
    }

    public Map<UUID, SbIsland> createIslands(Participants participants, ServerWorld world) {
        // preconditions
        Objects.requireNonNull(islandProtos, "Island prototypes must be loaded");
        Objects.requireNonNull(centerIsland, "Center island must be loaded");

        if (islandProtos.isEmpty()) {
            throw new IllegalStateException("No island prototypes available");
        }

        // place center island
        placeCenterIsland(world);

        int count = participants.count();

        // select island for each player
        List<SbIslandData> islandData = new ArrayList<>(count);
        List<BlockStructure> structures = new ArrayList<>(count);
        List<UUID> players = new ArrayList<>(count);

        for (ServerPlayerEntity player : participants) {
            players.add(player.getUuid());

            SbIslandProto island = islandProtos.get(random.nextInt(islandProtos.size()));
            islandData.add(island.data());
            structures.add(island.structure());
        }

        // generate circular positions for islands
        int minRadius = getMinRadius(structures);
        Vec2i[] offsets = CircleStructureGenerator.generateHorizontalOffsets(structures, minRadius);

        // place islands and collect the player mapping
        Map<UUID, SbIsland> islandMapping = new HashMap<>(count);

        CircleStructureGenerator.placeStructures(structures, world, offsets, (i, structure, circleOffset) -> {
            SbIslandData data = islandData.get(i);

            var absSpawn = data.spawn();
            var kibuOrigin = structure.getOrigin();
            BlockPos origin = new BlockPos(kibuOrigin.getX(), kibuOrigin.getY(), kibuOrigin.getZ());
            BlockPos spawn = absSpawn.subtract(origin);

            int x = circleOffset.x();
            int y = SPAWN_Y - spawn.getY();
            int z = circleOffset.z();

            BlockPos pos = new BlockPos(x, y, z);
            BlockBox bounds = StructureUtil.getBounds(structure).transform(AffineIntMatrix.makeTranslation(x, y, z));

            SbIsland island = new SbIsland(data, origin, pos, bounds);
            UUID uuid = players.get(i);

            islandMapping.put(uuid, island);

            return pos;
        });

        return islandMapping;
    }

    private void placeCenterIsland(ServerWorld world) {
        BlockStructure structure = centerIsland.structure();

        BlockPos structSpawn = centerIsland.spawn();
        KibuBlockPos origin = structure.getOrigin();

        BlockPos pos = new BlockPos(
                -structure.getWidth() / 2,
                SPAWN_Y - structSpawn.getY() + origin.getY(),
                -structure.getLength() / 2);

        StructureUtil.placeStructureFast(structure, world, pos);

        BlockPos spawn = structSpawn.add(
                pos.getX() - origin.getX(),
                pos.getY() - origin.getY(),
                pos.getZ() - origin.getZ());

        BreezeEntity breeze = new BreezeEntity(EntityType.BREEZE, world);
        breeze.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        breeze.setAiDisabled(true);
        breeze.setPersistent();
        breeze.setYaw(0);

        EntityAttributeInstance instance = breeze.getAttributeInstance(EntityAttributes.GENERIC_SCALE);

        if (instance != null) {
            instance.setBaseValue(10);
        }

        world.spawnEntity(breeze);

        aelosId = breeze.getUuid();
    }

    private int getMinRadius(List<BlockStructure> structures) {
        BlockStructure structure = centerIsland.structure();

        double centerIslandRadius = sqrt(pow(structure.getWidth(), 2) + pow(structure.getLength(), 2)) * 0.5;
        double largestIslandRadius = CircleStructureGenerator.computeLargestTangentDistance(structures) * 0.5;

        return (int) Math.ceil(centerIslandRadius + ISLAND_SPACING + largestIslandRadius);
    }

    public List<SbModule> getModules() {
        return Objects.requireNonNull(modules, "Modules not loaded yet");
    }

    public UUID getAelosId() {
        return Objects.requireNonNull(aelosId, "Aelos not created yet");
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
            int width = -1, height = -1, length = -1;

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

                BlockBox buildArea = data.buildArea();

                // ensure the build areas are compatible; the first island defines the dimensions
                if (width == -1 && height == -1 && length == -1) {
                    width = buildArea.getWidth();
                    height = buildArea.getHeight();
                    length = buildArea.getLength();
                } else if (width != buildArea.getWidth() || length != buildArea.getLength()) {
                    logger.warn("Incompatible build area dimensions. The first island defined {}x{} but island '{}' defines {}x{}",
                            width, length, data.id(), buildArea.getWidth(), buildArea.getLength());
                    continue;
                } else if (buildArea.getHeight() < height) {
                    logger.warn("Build area height of island {} is too small: {}. The first island defined height {}", data.id(), buildArea.getHeight(), height);
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

    private CompletableFuture<@Nullable CenterIsland> loadCenterIsland(GameMap map, ServerWorld world) {
        JSONObject json = map.requireProperty("center-island");
        String id = json.getString("id");

        Path path = getWorldDirectory(world).resolve("schematics").resolve(id + ".schem");
        BlockPos spawn = MapUtil.readBlockPos(json.getJSONArray("spawn"));

        return CompletableFuture.supplyAsync(() -> {
            BlockStructure structure = loadSchematic(path);

            if (structure == null) {
                return null;
            }

            return new CenterIsland(structure, spawn);
        });
    }

    private CompletableFuture<Set<SbModule>> loadModules(ServerWorld world, Vec3i dimensions) {
        Path dir = getWorldDirectory(world).resolve("schematics").resolve("module");

        return CompletableFuture.supplyAsync(() -> {
            try (var files = Files.list(dir)) {
                return files
                        .filter(path -> path.getFileName().toString().endsWith(".schem"))
                        .filter(Files::isRegularFile)
                        .map(this::loadModule)
                        .filter(Objects::nonNull)
                        .filter(module -> {
                            if (!module.isCompatibleWith(dimensions)) {
                                BlockStructure structure = module.structure();
                                logger.warn("Dimensions of module {} ({}x{}x{}) are not compatible with the island dimensions ({}x{}x{})",
                                        module.id(), structure.getWidth(), structure.getHeight(), structure.getLength(),
                                        dimensions.getX(), dimensions.getY(), dimensions.getZ());
                                return false;
                            }

                            return true;
                        })
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

        String id = path.getFileName().getFileName().toString();
        int idx = id.lastIndexOf('.');

        if (idx >= 0) {
            id = id.substring(0, idx);
        }

        return new SbModule(id, structure);
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

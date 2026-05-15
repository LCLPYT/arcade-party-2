package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.CircleStructureGenerator;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.mc.BlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.schematic.api.SchematicReader;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PbSetup {

    private final ServerLevel world;
    private final GameMap map;
    private final Logger logger;
    private @Nullable IndexedSet<PillarInfo> availablePillars = null;

    public PbSetup(ServerLevel world, GameMap map, Logger logger) {
        this.world = world;
        this.map = map;
        this.logger = logger;
    }

    public CompletableFuture<Void> load() {
        return loadAvailablePillars()
                .thenAccept(structs -> availablePillars = structs);
    }

    private CompletableFuture<IndexedSet<PillarInfo>> loadAvailablePillars() {
        return CompletableFuture.supplyAsync(() -> {
            var infos = new IndexedSet<PillarInfo>();

            Path dir = getWorldDirectory(world).resolve("schematics");
            JSONObject pillarConfig = map.requireProperty("pillars");

            for (String name : pillarConfig.keySet()) {
                JSONObject cfg = pillarConfig.optJSONObject(name);

                if (cfg == null) {
                    logger.warn("Unexpected config value for pillar {}; object expected", name);
                    continue;
                }

                Path path = dir.resolve(name + ".schem");

                if (!Files.isRegularFile(path)) {
                    logger.error("Pillar schematic file {} not found", path);
                    continue;
                }

                var info = loadPillar(path, cfg);

                if (info != null) {
                    infos.add(info);
                }
            }

            return infos;
        });
    }

    @Nullable
    private PillarInfo loadPillar(Path path, JSONObject cfg) {
        JSONArray spawnTuple = cfg.optJSONArray("spawn");

        if (spawnTuple == null) {
            logger.error("Pillar spawn is not configured for pillar {}", path);
            return null;
        }

        BlockPos spawn = MapUtil.readBlockPos(spawnTuple);

        SchematicReader reader = SchematicFormats.SPONGE_V2.reader();
        BlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();

        BlockStructure struct;

        try (var in = Files.newInputStream(path)) {
            struct = reader.read(in, adapter);
        } catch (IOException e) {
            logger.error("Failed to read pillar {}", path, e);
            return null;
        }

        return new PillarInfo(struct, spawn);
    }

    private Path getWorldDirectory(ServerLevel world) {
        var session = ((MinecraftServerAccessor) Objects.requireNonNull(world.getServer())).getStorageSource();

        return session.getDimensionPath(world.dimension());
    }

    @Nullable
    public PlacementResult placePillars(Participants participants, Random random) {
        var assignment = assignPillars(participants, random);

        if (assignment == null) return null;

        var structs = assignment.structs();

        // calculate pillar offsets in the world
        int minRadius = CircleStructureGenerator.calculateRadius(structs.size(), 9);

        Object mapMinRadius = map.getProperty("pillar-min-radius");

        if (mapMinRadius instanceof Number num) {
            minRadius = Math.max(minRadius, num.intValue());
        }

        var offsetResult = CircleStructureGenerator.generateHorizontalOffsetsRadius(structs, minRadius);

        // place pillars
        JSONArray centerTuple = map.requireProperty("pillar-center");
        BlockPos center = MapUtil.readBlockPos(centerTuple);

        Map<UUID, PositionRotation> mapping = new HashMap<>();
        var spawns = assignment.spawns();
        var playerIds = assignment.playerIds();

        CircleStructureGenerator.placeStructures(structs, world, offsetResult.offsets(), (i, _, offset) -> {
            BlockPos pillarSpawn = spawns.get(i);
            BlockPos pos = center.offset(offset.x(), -pillarSpawn.getY(), offset.z());
            BlockPos spawn = pos.offset(pillarSpawn);

            float yaw = (float) Math.toDegrees(Math.atan2(offset.x(), -offset.z()));

            UUID playerId = playerIds.get(i);
            mapping.put(playerId, new PositionRotation(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, yaw, 0));

            return pos;
        });

        return new PlacementResult(mapping, offsetResult.radius(), center);
    }

    @Nullable
    private Assignment assignPillars(Participants participants, Random random) {
        if (availablePillars == null || availablePillars.isEmpty()) {
            logger.error("No pillars available");
            return null;
        }

        int playerCount = participants.count();

        List<BlockStructure> structs = new ArrayList<>(playerCount);
        List<BlockPos> spawns = new ArrayList<>(playerCount);
        List<UUID> playerIds = new ArrayList<>(playerCount);

        List<PillarInfo> pool = new ArrayList<>(availablePillars.size());

        // randomize player order to get different neighbours each game
        var playerOrder = participants.stream().collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(playerOrder, random);

        for (ServerPlayer player : playerOrder) {
            playerIds.add(player.getUUID());

            if (pool.isEmpty()) {
                // refill pool if empty
                pool.addAll(availablePillars);
            }

            // retrieve random pillar variant from pool and then remove it for more variant diversity
            var info = pool.remove(random.nextInt(pool.size()));

            structs.add(info.struct);
            spawns.add(info.spawn);
        }

        return new Assignment(structs, spawns, playerIds);
    }

    public record PillarInfo(BlockStructure struct, BlockPos spawn) {}

    public record Assignment(List<BlockStructure> structs, List<BlockPos> spawns, List<UUID> playerIds) {}

    public record PlacementResult(Map<UUID, PositionRotation> spawns, int radius, BlockPos center) {}
}

package work.lclpnet.ap2.impl.util.world;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.util.world.AdjacentBlocks;
import work.lclpnet.ap2.util.world.SizedSpaceFinder;
import work.lclpnet.ap2.util.world.WorldScanner;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.game.map.MapUtils;

import java.util.*;


public class SpawnFinder {

    private static final boolean DEBUG_SCANNER = false;

    private final double spacingSquared;
    private final @Nullable DebugController debugController;

    public SpawnFinder(double spacing, @Nullable DebugController debugController) {
        this.spacingSquared = spacing * spacing;
        this.debugController = debugController;
    }

    public List<Vec3> findSpawns(ServerLevel world, GameMap map) {
        BlockBox bounds = MapUtil.readBox(map.requireProperty("bounds"));

        Vec3 spawnPosition = MapUtils.getSpawnPosition(map);

        BlockPos start = new BlockPos(
                (int) Math.floor(spawnPosition.x()),
                (int) Math.floor(spawnPosition.y()),
                (int) Math.floor(spawnPosition.z())
        );

        return findSpawns(world, bounds, Set.of(start));
    }

    public List<Vec3> findSpawns(ServerLevel world, BlockBox bounds, Set<BlockPos> starts) {
        BlockPredicate predicate = BlockPredicate.and(bounds::contains, new WalkableBlockPredicate(world));
        AdjacentBlocks adjacent = new SimpleAdjacentBlocks(predicate, 1, 4);
        WorldScanner scanner = new BfsWorldScanner(adjacent);

        if (DEBUG_SCANNER) {
            debugScanner(scanner, starts);
        }

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, EntityTypes.PLAYER);

        return spaceFinder.findSpaces(scanner.scan(starts));
    }

    private void debugScanner(WorldScanner scanner, Set<BlockPos> starts) {
        if (debugController == null) return;

        var positions = new ArrayList<BlockPos>();
        var it = scanner.scan(starts);

        while (it.hasNext()) {
            positions.add(it.next());
        }

        debugController.visualizeBlockPositions(positions, Blocks.STAINED_GLASS.green().defaultBlockState());
    }

    public List<Vec3> generateSpacedSpawns(List<Vec3> spawns, int count, Random random) {
        if (count <= 0 || spawns.isEmpty()) {
            return List.of();
        }

        List<Vec3> spaced = new ArrayList<>();
        var spawnsByDistance = new Object2DoubleOpenHashMap<Vec3>(spawns.size());
        boolean distanceDirty = false;

        for (Vec3 spawn : spawns) {
            spawnsByDistance.put(spawn, Double.MAX_VALUE);
        }

        for (int i = 0; i < count; i++) {
            if (distanceDirty) {
                updateDistances(spawnsByDistance, spaced);
                distanceDirty = false;
            }

            var distantSpawns = spawnsByDistance.object2DoubleEntrySet().stream()
                    .filter(entry -> entry.getDoubleValue() >= spacingSquared)
                    .map(Map.Entry::getKey)
                    .toArray(Vec3[]::new);

            if (distantSpawns.length == 0) break;

            Vec3 spawn = distantSpawns[random.nextInt(distantSpawns.length)];
            spaced.add(spawn);

            distanceDirty = true;
        }

        if (spaced.size() >= count) {
            return spaced;
        }

        // fill remaining space with the least crowded spawns
        for (int i = spaced.size(); i < count; i++) {
            if (distanceDirty) {
                updateDistances(spawnsByDistance, spaced);
            }

            var leastCrowded = spawnsByDistance.object2DoubleEntrySet().stream()
                    .max(Comparator.comparingDouble(Object2DoubleMap.Entry::getDoubleValue))
                    .map(Map.Entry::getKey)
                    .orElseThrow();

            spaced.add(leastCrowded);
            distanceDirty = true;
        }

        return spaced;
    }

    private void updateDistances(Object2DoubleOpenHashMap<Vec3> spawnsByDistance, List<Vec3> spaced) {
        var it = spawnsByDistance.object2DoubleEntrySet().fastIterator();

        while (it.hasNext()) {
            var entry = it.next();
            Vec3 pos = entry.getKey();
            entry.setValue(distanceSq(pos, spaced));
        }
    }

    private double distanceSq(Vec3 pos, List<Vec3> spaced) {
        return spaced.stream()
                .mapToDouble(x -> x.distanceToSqr(pos))
                .min().orElse(Double.MAX_VALUE);
    }
}

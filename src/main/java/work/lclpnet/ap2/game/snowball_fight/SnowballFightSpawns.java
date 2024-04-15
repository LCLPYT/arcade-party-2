package work.lclpnet.ap2.game.snowball_fight;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.api.util.world.WorldScanner;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.ap2.impl.util.world.SizedSpaceFinder;
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.*;

public class SnowballFightSpawns {

    private final double spacingSquared;

    public SnowballFightSpawns(double spacing) {
        this.spacingSquared = spacing * spacing;
    }

    public List<Vec3d> findSpawns(ServerWorld world, GameMap map) {
        BlockBox bounds = MapUtil.readBox(map.requireProperty("bounds"));
        Vec3d spawnPosition = MapUtils.getSpawnPosition(map);
        BlockPos start = new BlockPos(
                (int) Math.floor(spawnPosition.getX()),
                (int) Math.floor(spawnPosition.getY()),
                (int) Math.floor(spawnPosition.getZ()));

        BlockPredicate predicate = BlockPredicate.and(bounds::contains, new WalkableBlockPredicate(world));
        AdjacentBlocks adjacent = new SimpleAdjacentBlocks(predicate, 1);
        WorldScanner scanner = new BfsWorldScanner(adjacent);

        SizedSpaceFinder spaceFinder = SizedSpaceFinder.create(world, EntityType.PLAYER);
        return spaceFinder.findSpaces(scanner.scan(start));
    }

    public List<Vec3d> generateSpacedSpawns(List<Vec3d> spawns, int count, Random random) {
        if (count <= 0 || spawns.isEmpty()) {
            return List.of();
        }

        List<Vec3d> spaced = new ArrayList<>();
        var spawnsByDistance = new Object2DoubleOpenHashMap<Vec3d>(spawns.size());
        boolean distanceDirty = false;

        for (Vec3d spawn : spawns) {
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
                    .toArray(Vec3d[]::new);

            if (distantSpawns.length == 0) break;

            Vec3d spawn = distantSpawns[random.nextInt(distantSpawns.length)];
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

    private void updateDistances(Object2DoubleOpenHashMap<Vec3d> spawnsByDistance, List<Vec3d> spaced) {
        var it = spawnsByDistance.object2DoubleEntrySet().fastIterator();

        while (it.hasNext()) {
            var entry = it.next();
            Vec3d pos = entry.getKey();
            entry.setValue(distanceSq(pos, spaced));
        }
    }

    private double distanceSq(Vec3d pos, List<Vec3d> spaced) {
        return spaced.stream()
                .mapToDouble(x -> x.squaredDistanceTo(pos))
                .min().orElse(Double.MAX_VALUE);
    }
}

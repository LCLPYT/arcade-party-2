package work.lclpnet.ap2.game.one_in_the_chamber;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.math.BlockPos;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.map.MapUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static work.lclpnet.ap2.game.one_in_the_chamber.OneInTheChamberInstance.RESPAWN_SPACING;

public class OneInTheChamberSpawns {

    private final double SQUARED_RESPAWN_DISTANCE = RESPAWN_SPACING * RESPAWN_SPACING;
    private final MiniGameHandle gameHandle;
    private final Random random;
    private List<BlockPos> spawnPoints;

    public OneInTheChamberSpawns(MiniGameHandle gameHandle, Random random) {
        this.gameHandle = gameHandle;
        this.random = random;
    }

    public void loadSpawnPoints(JSONArray array) {
        spawnPoints = new ArrayList<>(array.length());

        for (Object object : array) {
            if (object instanceof JSONArray tuple) {
                spawnPoints.add(MapUtil.readBlockPos(tuple));
            }
        }
    }

    public BlockPos getRandomSpawn() {
        var spawnsByDistance = spawnPoints.stream()
                .map(pos -> Pair.of(pos, minPlayerSquaredDistance(pos)))
                .toList();

        var distantSpawns = spawnPoints.stream()
                .filter(pos -> minPlayerSquaredDistance(pos) >= SQUARED_RESPAWN_DISTANCE)
                .toArray(BlockPos[]::new);

        if (distantSpawns.length == 0) {
            // no spawns with desired distance exist, fallback to most uncrowded
            return spawnsByDistance.stream()
                    .max(Comparator.comparingDouble(Pair::right))
                    .map(Pair::left)
                    .orElseThrow();
        }

        return distantSpawns[random.nextInt(distantSpawns.length)];
    }

    private double minPlayerSquaredDistance(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        return gameHandle.getParticipants().stream()
                .filter(player -> !player.isSpectator())
                .mapToDouble(player -> player.getPos().squaredDistanceTo(x, y, z))
                .min().orElse(Double.MAX_VALUE);
    }
}



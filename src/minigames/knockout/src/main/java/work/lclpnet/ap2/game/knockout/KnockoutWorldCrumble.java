package work.lclpnet.ap2.game.knockout;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.json.JSONObject;
import work.lclpnet.ap2.game.knockout.util.DistanceIterator;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.gaco.math.Vec2i;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public class KnockoutWorldCrumble {

    private static final int DEFAULT_DELAY_SECONDS = 90, DEFAULT_PERIOD_TICKS = Ticks.seconds(1);
    private final ServerLevel world;
    private final GameMap map;
    private short[][] distances = null;
    private int centerX = 0, centerZ = 0;
    private short radius = 0;
    private short currentDistance = -1;
    private boolean warn = true;
    private int delaySeconds = DEFAULT_DELAY_SECONDS;
    private int periodTicks = DEFAULT_PERIOD_TICKS;

    public KnockoutWorldCrumble(ServerLevel world, GameMap map) {
        this.world = world;
        this.map = map;
    }

    public void init() {
        JSONObject crumble = map.requireProperty("crumble");

        if (!crumble.has("radius")) {
            throw new IllegalStateException("Property radius is undefined");
        }

        this.radius = (short) Math.abs(crumble.getNumber("radius").shortValue());

        if (crumble.has("center")) {
            Vec2i center = MapUtil.readVec2i(crumble.getJSONArray("center"));
            this.centerX = center.x();
            this.centerZ = center.z();
        } else {
            this.centerX = 0;
            this.centerZ = 0;
        }

        if (crumble.has("delay")) {
            delaySeconds = Math.max(0, crumble.getNumber("delay").intValue());
        }

        if (crumble.has("period")) {
            periodTicks = Math.max(1, crumble.getNumber("period").intValue());
        }

        this.distances = buildDistancesArray(radius);
        this.currentDistance = findFurthestDistance(this.distances);
    }

    private short findFurthestDistance(short[][] distances) {
        final int maxDistance = distances[0][0];

        for (int r = maxDistance; r >= 0 ; r--) {
            for (BlockPos pos : iterateBlocks(r)) {
                if (world.isEmptyBlock(pos)) continue;

                return (short) Math.round(Math.sqrt(Math.pow(centerX - pos.getX(), 2) + Math.pow(centerZ - pos.getZ(), 2)));
            }
        }

        return -1;
    }

    static short[][] buildDistancesArray(int radius) {
        final short[][] distances = new short[2 * radius + 1][2 * radius + 1];

        for (int x = -radius; x <= radius; x++) {
            int xSquared = x * x;
            int ix = x + radius;

            for (int z = -radius; z <= radius; z++) {
                short distance = (short) Math.round(Math.sqrt(xSquared + z * z));
                distances[ix][z + radius] = distance;
            }
        }

        return distances;
    }

    public void start(TaskScheduler scheduler) {
        scheduler.interval(this::tick, periodTicks);
    }

    private void tick(RunningTask task) {
        if (currentDistance < 0) {
            task.cancel();
            return;
        }

        if (warn) {
            warn = false;
            markBlocks();
            return;
        }

        removeBlocks();
        currentDistance--;
        warn = true;
    }

    private void markBlocks() {
        BlockState markerState = Blocks.RED_TERRACOTTA.defaultBlockState();

        for (BlockPos pos : iterateBlocks(currentDistance)) {
            BlockState state = world.getBlockState(pos);

            if (!state.isCollisionShapeFullBlock(world, pos)) continue;

            world.setBlock(pos, markerState, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS);
        }
    }

    private void removeBlocks() {
        BlockState air = Blocks.AIR.defaultBlockState();

        for (BlockPos pos : iterateBlocks(currentDistance)) {
            BlockState state = world.getBlockState(pos);

            if (state.isAir()) continue;

            world.setBlock(pos, air, Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS);
        }
    }

    private Iterable<BlockPos> iterateBlocks(int targetDistance) {
        return () -> new DistanceIterator(radius, centerX, centerZ, world.getMinY(), world.getMaxY(), distances, targetDistance);
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }
}

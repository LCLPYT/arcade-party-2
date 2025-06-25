package work.lclpnet.ap2.game.dragon_escape;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.SplinePath;
import work.lclpnet.ap2.impl.util.debug.SplinePathDebugger;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DragonEscapeInstance extends FFAGameInstance {

    private static final boolean DEBUG_PATH = true;

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> completed = new OrderedDataContainer<>(PlayerRef::create);
    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> score = new ScoreDataContainer<>(PlayerRef::create);
    private final CombinedDataContainer<ServerPlayerEntity, PlayerRef> data = new CombinedDataContainer<>(List.of(completed, score));
    private final Random random = new Random();

    private SplinePath path = null;

    public DragonEscapeInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        JSONArray keypointsJson = getMap().getProperties().getJSONArray("dragon-path");
        Logger logger = gameHandle.getLogger();

        var path = SplinePath.readCentered(keypointsJson, logger).orElse(null);

        if (path == null) {
            logger.error("Failed to create dragon path, aborting game...");
            gameHandle.complete(MiniGameResults.EMPTY);
            return;
        }

        this.path = path;

        teleportPlayers();

        if (DEBUG_PATH) {
            SplinePathDebugger.debug(commons().debugController(), path, 1000);
        }
    }

    private void teleportPlayers() {
        JSONObject shapeJson = getMap().getProperties().getJSONObject("spawn-shape");
        BlockShape spawnShape = MapUtil.readShape(shapeJson);

        List<BlockPos> spawnPool = new ArrayList<>();

        for (BlockPos pos : spawnShape) {
            spawnPool.add(pos.toImmutable());
        }

        if (spawnPool.isEmpty()) {
            gameHandle.getLogger().error("Spawn shape is empty");
            return;
        }

        List<BlockPos> spawns = new ArrayList<>(spawnPool);

        ServerWorld world = getWorld();
        float yaw = MapUtils.getSpawnYaw(getMap());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            if (spawns.isEmpty()) {
                spawns.addAll(spawnPool);
            }

            BlockPos pos = spawns.remove(random.nextInt(spawns.size()));

            player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), yaw, 0f, true);
        }
    }

    @Override
    protected void ready() {

    }
}

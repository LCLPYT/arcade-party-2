package work.lclpnet.ap2.game.red_light_green_light;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.*;

public class RedLightGreenLightInstance extends DefaultGameInstance {

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> data = new OrderedDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final Set<UUID> inGoal = new HashSet<>();
    private final List<TrafficLight> trafficLights = new ArrayList<>();
    private BlockBox goal;

    public RedLightGreenLightInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        GameMap map = getMap();
        ServerWorld world = getWorld();

        goal = MapUtil.readBox(map.requireProperty("goal"));
        BlockBox spawnArea = MapUtil.readBox(map.requireProperty("spawn-area"));
        float yaw = MapUtils.getSpawnYaw(map);

        for (ServerPlayerEntity participant : gameHandle.getParticipants()) {
            Vec3d pos = spawnArea.getRandomPos(random);
            participant.teleport(world, pos.getX(), pos.getY(), pos.getZ(), yaw, 0f);
        }

        readTrafficLights();
        setStatus(TrafficLight.Status.RED);
    }

    @Override
    protected void ready() {
        PlayerMoveCallback.HOOK.register((player, from, to) -> {
            this.onMove(player, to);
            return false;
        });

        openGate();
        setStatus(TrafficLight.Status.GREEN);
    }

    private void readTrafficLights() {
        JSONArray json = getMap().getProperty("traffic-lights");

        if (json == null) return;

        trafficLights.clear();

        for (Object obj : json) {
            if (!(obj instanceof JSONObject jsonObj)) continue;

            trafficLights.add(TrafficLight.fromJson(jsonObj));
        }
    }

    private void setStatus(TrafficLight.Status status) {
        ServerWorld world = getWorld();

        for (TrafficLight light : trafficLights) {
            light.set(EnumSet.of(status), world);
        }
    }

    private void openGate() {
        BlockBox gate = MapUtil.readBox(getMap().requireProperty("gate"));
        ServerWorld world = getWorld();
        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : gate) {
            world.setBlockState(pos, air);
        }
    }

    private void onMove(ServerPlayerEntity player, PositionRotation to) {
        if (winManager.isGameOver()
            || !gameHandle.getParticipants().isParticipating(player)
            || inGoal.contains(player.getUuid())) return;

        if (goal.collidesWith(to)) {
            onGoalReached(player);
            return;
        }
    }

    private void onGoalReached(ServerPlayerEntity player) {
        data.add(player);
        inGoal.add(player.getUuid());

        int requiredForOver = Math.min(3, gameHandle.getParticipants().count());

        if (inGoal.size() >= requiredForOver) {
            winManager.win(data.getBestSubject(resolver).orElse(null));
        }
    }
}

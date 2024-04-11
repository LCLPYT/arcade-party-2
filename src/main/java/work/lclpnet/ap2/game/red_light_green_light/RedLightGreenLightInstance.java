package work.lclpnet.ap2.game.red_light_green_light;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.inv.item.FireworkExplosion;
import work.lclpnet.kibu.inv.item.FireworkUtil;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.*;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class RedLightGreenLightInstance extends DefaultGameInstance implements Runnable {

    private static final int STOP_MIN_TICKS = 30, STOP_MAX_TICKS = 80;
    private static final int WARN_MIN_TICKS = 15, WARN_MAX_TICKS = 40;
    private static final int GO_MIN_TICKS = 60, GO_MAX_TICKS = 120;
    private static final int MAX_GAME_TIME_TICKS = Ticks.minutes(3);
    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> data = new OrderedDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final Set<UUID> inGoal = new HashSet<>();
    private final List<TrafficLight> trafficLights = new ArrayList<>();
    private TranslatedBossBar taskBar = null;
    private BlockBox goal;
    private int nextStop = 0;
    private int nextWarn = 0;
    private int nextGo = 0;
    private int absTime = 0;

    public RedLightGreenLightInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        taskBar = useTaskDisplay();

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
        setTrafficLightStatus(EnumSet.noneOf(TrafficLight.Status.class));
    }

    @Override
    protected void ready() {
        PlayerMoveCallback.HOOK.register((player, from, to) -> {
            this.onMove(player, to);
            return false;
        });

        openGate();
        scheduleNextStop();
        setStatus(TrafficLight.Status.GREEN);

        gameHandle.getGameScheduler().interval(this, 1);
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
        setTrafficLightStatus(EnumSet.of(status));

        taskBar.setColor(switch (status) {
            case RED -> BossBar.Color.RED;
            case YELLOW -> BossBar.Color.YELLOW;
            case GREEN -> BossBar.Color.GREEN;
        });

        String key = switch (status) {
            case RED -> "game.ap2.red_light_green_light.stop";
            case YELLOW -> "game.ap2.red_light_green_light.warn";
            case GREEN -> "game.ap2.red_light_green_light.go";
        };

        var msg = gameHandle.getTranslations().translateText(key).formatted(BOLD, switch (status) {
            case RED -> RED;
            case YELLOW -> YELLOW;
            case GREEN -> GREEN;
        });

        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            switch (status) {
                case RED -> player.playSound(SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.NEUTRAL, 1f, 0.5f);
                case YELLOW -> player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1f, 0.5f);
                case GREEN -> player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 1f, 1f);
            }

            Title.get(player).title(msg.translateFor(player));
        }
    }

    private void setTrafficLightStatus(EnumSet<TrafficLight.Status> status) {
        ServerWorld world = getWorld();

        for (TrafficLight light : trafficLights) {
            light.set(status, world);
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

        FireworkExplosion explosion = new FireworkExplosion(FireworkRocketItem.Type.LARGE_BALL, true, false, new int[] {0x20FF4D}, new int[] {0x1E7220});

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkUtil.setExplosions(rocket, explosion);
        FireworkUtil.setFlight(rocket, (byte) 1);

        ServerWorld world = getWorld();
        FireworkRocketEntity firework = new FireworkRocketEntity(world, player.getX(), player.getY(), player.getZ(), rocket);
        world.spawnEntity(firework);

        FireworkEntityAccess.explode(firework);

        TranslationService translations = gameHandle.getTranslations();
        int requiredForOver = Math.min(3, gameHandle.getParticipants().count());

        if (inGoal.size() >= requiredForOver) {
            RootText msg = translations.translateText(player, "game.ap2.red_light_green_light.goal").formatted(GREEN);
            player.sendMessage(msg);

            winManager.win(data.getBestSubject(resolver).orElse(null));
        } else {
            var msg = translations.translateText(player, "game.ap2.red_light_green_light.goal_remain",
                    styled(requiredForOver, YELLOW))
                    .formatted(GREEN);

            player.sendMessage(msg);
        }
    }

    private void scheduleNextStop() {
        nextStop = STOP_MIN_TICKS + random.nextInt(STOP_MAX_TICKS - STOP_MIN_TICKS + 1);

        int randomNextWarn = WARN_MIN_TICKS + random.nextInt(WARN_MAX_TICKS - WARN_MIN_TICKS + 1);
        nextWarn = Math.max(1, Math.min(nextStop - WARN_MIN_TICKS, randomNextWarn));

        nextGo = GO_MIN_TICKS + random.nextInt(GO_MAX_TICKS - GO_MIN_TICKS + 1);
    }

    @Override
    public void run() {
        if (absTime++ >= MAX_GAME_TIME_TICKS) {
            ServerWorld world = getWorld();
            TranslationService translations = gameHandle.getTranslations();

            translations.translateText("game.ap2.red_light_green_light.timeout")
                    .formatted(RED)
                    .sendTo(PlayerLookup.world(world));

            winManager.win(data.getBestSubject(resolver).orElse(null));
            return;
        }

        int relTime = nextStop--;

        if (relTime < 0) {
            if (relTime == -nextGo) {
                scheduleNextStop();
                setStatus(TrafficLight.Status.GREEN);
            }
            return;
        }

        if (relTime == 0) {
            setStatus(TrafficLight.Status.RED);
            return;
        }

        if (relTime == nextWarn) {
            setStatus(TrafficLight.Status.YELLOW);
        }
    }
}

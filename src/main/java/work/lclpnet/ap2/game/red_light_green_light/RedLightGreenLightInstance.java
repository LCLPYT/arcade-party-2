package work.lclpnet.ap2.game.red_light_green_light;

import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.movement.TickMovementDetector;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.FireworkEntityAccess;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.LocalizedFormat;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;
import work.lclpnet.lobby.util.RayCaster;

import java.util.*;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class RedLightGreenLightInstance extends DefaultGameInstance implements Runnable {

    private static final int STOP_MIN_TICKS = 60, STOP_MAX_TICKS = 90;
    private static final int WARN_MIN_TICKS = 35, WARN_MAX_TICKS = 75;
    private static final int GO_MIN_TICKS = 60, GO_MAX_TICKS = 120;
    private static final int END_TIME_SECONDS = 15;
    private final SimpleMovementBlocker movementBlocker;
    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> data = new OrderedDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final Set<UUID> inGoal = new HashSet<>();
    private final Set<UUID> moved = new HashSet<>();
    private final List<TrafficLight> trafficLights = new ArrayList<>();
    private MovementTracker tracker = null;
    private TranslatedBossBar taskBar = null;
    private BlockBox goal;
    private int timer = 0;
    private int warn = 0;
    private int go = 0;
    private int gameEnd = -1;

    public RedLightGreenLightInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        movementBlocker = new SimpleMovementBlocker(gameHandle.getGameScheduler());
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
        tracker = new MovementTracker(goal);

        BlockBox spawnArea = MapUtil.readBox(map.requireProperty("spawn-area"));
        float yaw = MapUtils.getSpawnYaw(map);

        for (ServerPlayerEntity participant : gameHandle.getParticipants()) {
            Vec3d pos = spawnArea.randomPos(random);
            participant.teleport(world, pos.getX(), pos.getY(), pos.getZ(), yaw, 0f);
        }

        readTrafficLights();
        setTrafficLightStatus(EnumSet.noneOf(TrafficLight.Status.class));

        movementBlocker.init(gameHandle.getHookRegistrar());

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);
    }

    @Override
    protected void ready() {
        TickMovementDetector detector = new TickMovementDetector(gameHandle::getParticipants);
        detector.register(this::onMove);
        detector.init(gameHandle.getGameScheduler(), gameHandle.getHookRegistrar());

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
        if (status == TrafficLight.Status.GREEN) {
            for (UUID uuid : moved) {
                ServerPlayerEntity player = gameHandle.getServer().getPlayerManager().getPlayer(uuid);

                if (player == null) continue;

                movementBlocker.enableMovement(player);
            }

            moved.clear();
        }

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
                case RED -> player.playSoundToPlayer(SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.NEUTRAL, 1f, 0.5f);
                case YELLOW -> player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1f, 0.5f);
                case GREEN -> player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 1f, 1f);
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

    private void onMove(ServerPlayerEntity player) {
        if (winManager.isGameOver()
            || !gameHandle.getParticipants().isParticipating(player)
            || inGoal.contains(player.getUuid())) return;

        double x = player.getX(), y = player.getY(), z = player.getZ();

        if (goal.collidesWith(x, y, z)) {
            onGoalReached(player);
            return;
        }

        if (timer > 0) {
            tracker.track(player);
            return;
        }

        // moved while stopped, check if the player is already tracked as "moved"
        if (!moved.add(player.getUuid())) return;

        punish(player);
    }

    private void punish(ServerPlayerEntity player) {
        ServerWorld world = getWorld();

        double x = player.getX(), y = player.getY(), z = player.getZ();
        world.spawnParticles(ParticleTypes.CRIT, x, y, z, 100, 0.1, 0.1, 0.1, 1);

        // find a suitable position to reset the player to
        Vec3d pos = tracker.getMostDistantPos(player);

        if (pos != null) {
            world.playSound(player, x, y, z, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.5f, 1f);

            x = pos.getX();
            y = findSuitableY(world, pos);
            z = pos.getZ();

            player.teleport(world, x, y, z, player.getYaw(), player.getPitch());
            player.playSoundToPlayer(SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.5f, 1f);
        } else {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.5f, 1f);
        }

        movementBlocker.disableMovement(player);

        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.red_light_green_light.moved").formatted(RED);
        player.sendMessage(msg);
    }

    private double findSuitableY(ServerWorld world, Vec3d pos) {
        var ctx = new RayCaster.GenericRaycastContext(pos, pos.subtract(0, 10, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE);
        BlockHitResult hit = RayCaster.rayCastBlockCollision(world, ctx);

        return ((int) Math.round(hit.getPos().getY() * 20)) / 20d;
    }

    private void onGoalReached(ServerPlayerEntity player) {
        data.add(player);
        inGoal.add(player.getUuid());

        FireworkExplosionComponent explosion = new FireworkExplosionComponent(FireworkExplosionComponent.Type.LARGE_BALL, IntList.of(0x20FF4D), IntList.of(0x1E7220), false, true);

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1, List.of(explosion)));

        ServerWorld world = getWorld();
        FireworkRocketEntity firework = new FireworkRocketEntity(world, player.getX(), player.getY(), player.getZ(), rocket);
        world.spawnEntity(firework);

        FireworkEntityAccess.explode(firework);

        Translations translations = gameHandle.getTranslations();

        if (inGoal.size() >= gameHandle.getParticipants().count()) {
            winManager.win(data.getBestSubject(resolver).orElse(null));
        } else if (gameEnd == -1) {
            translations.translateText("game.ap2.red_light_green_light.goal",
                            styled(player.getNameForScoreboard(), YELLOW),
                            styled(END_TIME_SECONDS, YELLOW))
                    .formatted(GREEN)
                    .sendTo(PlayerLookup.world(world));

            gameEnd = Ticks.seconds(END_TIME_SECONDS);
        }
    }

    private void scheduleNextStop() {
        timer = STOP_MIN_TICKS + random.nextInt(STOP_MAX_TICKS - STOP_MIN_TICKS + 1);

        int randomNextWarn = WARN_MIN_TICKS + random.nextInt(WARN_MAX_TICKS - WARN_MIN_TICKS + 1);
        warn = Math.max(1, Math.min(timer - WARN_MIN_TICKS, randomNextWarn));

        go = GO_MIN_TICKS + random.nextInt(GO_MAX_TICKS - GO_MIN_TICKS + 1);
    }

    @Override
    public void run() {
        if (gameEnd >= 0) {
            int ticksUntilEnd = gameEnd--;

            if (ticksUntilEnd % 20 == 0) {
                taskBar.setPercent(ticksUntilEnd / 20f / END_TIME_SECONDS);
            }

            if (ticksUntilEnd == 0) {
                gradePlayers();
                winManager.win(data.getBestSubject(resolver).orElse(null));
                return;
            }
        }

        int relTime = timer--;

        if (relTime < 0) {
            if (relTime == -go) {
                scheduleNextStop();
                setStatus(TrafficLight.Status.GREEN);
            }
            return;
        }

        if (relTime == 0) {
            setStatus(TrafficLight.Status.RED);
            return;
        }

        if (relTime == warn) {
            setStatus(TrafficLight.Status.YELLOW);
        }
    }

    private void gradePlayers() {
        Translations translations = gameHandle.getTranslations();

        // grade players who are not yet in the goal by their distance to the goal
        gameHandle.getParticipants().stream()
                .filter(player -> !inGoal.contains(player.getUuid()))
                .map(player -> {
                    double distanceSq = goal.squaredDistanceTo(player.getPos());
                    return new Grade(player, Math.sqrt(distanceSq));
                })
                .sorted(Comparator.comparingDouble(Grade::distance))
                .forEachOrdered(grade -> {
                    var detail = translations.translateText("game.ap2.red_light_green_light.result", LocalizedFormat.format("%.1f", grade.distance));
                    data.add(grade.player(), detail);
                });
    }

    private record Grade(ServerPlayerEntity player, double distance) {}
}

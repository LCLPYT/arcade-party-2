package work.lclpnet.ap2.game.splashy_dropper;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.splashy_dropper.data.SdGenerator;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.util.collision.GroundDetector;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.minecraft.util.Formatting.YELLOW;

public class SplashyDropperInstance extends DefaultGameInstance implements MapBootstrapFunction {

    private static final int MIN_DURATION_SECONDS = 50, MAX_DURATION_SECONDS = 75;
    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final List<BlockPos> blocksBelow = new ArrayList<>();
    private final SimpleMovementBlocker movementBlocker;
    private BfsWorldScanner worldScanner = null;
    private GroundDetector groundDetector = null;
    private double minSpawnY = 70;

    public SplashyDropperInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementBlocker = new SimpleMovementBlocker(gameHandle.getScheduler());
        movementBlocker.setModifySpeedAttribute(false);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        // run the bootstrap on the server thread, because the scanWorld method of SdGenerator will run faster
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, world.getServer());

        new SdGenerator(world, map, random).generate();
    }

    @Override
    protected void prepare() {
        commons().teleportToRandomSpawns(random);

        HookRegistrar hooks = gameHandle.getHookRegistrar();
        movementBlocker.init(hooks);

        ServerWorld world = getWorld();
        var adj = new SimpleAdjacentBlocks(pos -> world.getFluidState(pos).isIn(FluidTags.WATER), 0);
        worldScanner = new BfsWorldScanner(adj);

        groundDetector = new GroundDetector(world, 0.35);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.disableMovement(player);
        }

        minSpawnY = commons().getSpawns().stream()
                .mapToDouble(PositionRotation::getY)
                .min().orElse(70);

        setupObjective();
        setupTeam();
    }

    @Override
    protected void ready() {
        Translations translations = gameHandle.getTranslations();
        var subject = translations.translateText(gameHandle.getGameInfo().getTaskKey());

        int duration = MIN_DURATION_SECONDS + random.nextInt(MAX_DURATION_SECONDS - MIN_DURATION_SECONDS + 1);
        commons().createTimer(subject, duration).whenDone(this::onTimerDone);

        gameHandle.getGameScheduler().interval(this::tick, 1);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.enableMovement(player);
        }
    }

    private void setupTeam() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        Translations translations = gameHandle.getTranslations();
        VisibilityHandler visibility = new VisibilityHandler(new VisibilityManager(team), translations, gameHandle.getParticipants());
        visibility.init(gameHandle.getHookRegistrar());

        visibility.giveItems();
    }

    private void setupObjective() {
        var objective = gameHandle.getScoreboardManager().translateObjective("score", "game.ap2.chicken_shooter.points")
                .formatted(YELLOW, Formatting.BOLD);

        useScoreboardStatsSync(data, objective);
        objective.setSlot(ScoreboardDisplaySlot.LIST);
        objective.setNumberFormat(StyledNumberFormat.YELLOW);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            objective.addPlayer(player);
        }
    }

    private void tick() {
        if (winManager.isGameOver()) return;

        ServerWorld world = getWorld();

        outer: for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            if (player.getY() >= minSpawnY) continue;

            if (world.getFluidState(player.getBlockPos()).isIn(FluidTags.WATER)) {
                onLandInWater(player);
                continue;
            }

            blocksBelow.clear();
            groundDetector.collectBlocksBelow(player, blocksBelow);

            for (BlockPos pos : blocksBelow) {
                BlockState state = world.getBlockState(pos);

                if (state.getCollisionShape(world, pos, ShapeContext.of(player)).isEmpty()) continue;

                onHitGround(player);

                continue outer;
            }
        }
    }

    private void onLandInWater(ServerPlayerEntity player) {
        int count = removeWater(player.getBlockPos());
        int score = (int) Math.round(Math.sqrt(count));

        score = Math.max(0, Math.min(3, 4 - score));

        commons().addScore(player, score, data);

        float pitch = switch (score) {
            case 2 -> 1.6f;
            case 3 -> 1.8f;
            default -> 1.4f;
        };

        gameHandle.getGameScheduler().immediate(() -> player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, pitch));

        commons().teleportToRandomSpawn(player, random);
    }

    private void onHitGround(ServerPlayerEntity player) {
        commons().teleportToRandomSpawn(player, random);

        commons().teleportToRandomSpawn(player, random);

        gameHandle.getGameScheduler().immediate(() -> player.playSoundToPlayer(SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.PLAYERS, 0.25f, 0.5f));
    }

    private int removeWater(BlockPos pos) {
        ServerWorld world = getWorld();
        var it = worldScanner.scan(pos);
        int count = 0;
        BlockState air = Blocks.AIR.getDefaultState();

        while (it.hasNext()) {
            BlockPos waterPos = it.next();

            world.setBlockState(waterPos, air);

            count++;
        }

        return count;
    }

    private void onTimerDone() {
        winManager.win(data.getBestSubject(resolver).orElse(null));
    }
}

package work.lclpnet.ap2.game.splashy_dropper;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.splashy_dropper.data.SdGenerator;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.DataContainers;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.util.handler.Visibility;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner;
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks;
import work.lclpnet.combatctl.impl.CombatStyles;
import work.lclpnet.gaco.collisions.util.GroundDetector;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.minecraft.ChatFormatting.YELLOW;

public class SplashyDropperInstance extends FFAGameInstance implements MapBootstrapFunction {

    private static final int MIN_DURATION_SECONDS = 50, MAX_DURATION_SECONDS = 75;
    private final IntDataContainer<ServerPlayer, PlayerRef> data;
    private final Random random = new Random();
    private final List<BlockPos> blocksBelow = new ArrayList<>();
    private final SimpleMovementBlocker movementBlocker;
    private BfsWorldScanner worldScanner = null;
    private GroundDetector groundDetector = null;
    private double minSpawnY = 70;

    public SplashyDropperInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create);

        movementBlocker = new SimpleMovementBlocker(gameHandle.getRootScheduler());
        movementBlocker.setModifySpeedAttribute(false);

        gameHandle.getPlayerUtil().setDefaultCombatStyle(CombatStyles.CLASSIC
                .andThen(player -> player.setDisableOldBobbing(true), _ -> {}));
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        // run the bootstrap on the server thread, because the scanWorld method of SdGenerator will run faster
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(@NotNull ServerLevel world, @NotNull GameMap map) {
        world.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, world.getServer());

        new SdGenerator(world, map, random).generate();
    }

    @Override
    protected void prepare() {
        setupObjective();
        setupTeam();

        commons().teleportToRandomSpawns(random);

        HookRegistrar hooks = gameHandle.getHooks();
        movementBlocker.init(hooks);

        ServerLevel world = getWorld();
        var adj = new SimpleAdjacentBlocks(pos -> world.getFluidState(pos).is(FluidTags.WATER), 0);
        worldScanner = new BfsWorldScanner(adj);

        groundDetector = new GroundDetector(world, 0.35);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            movementBlocker.disableMovement(player);
        }

        minSpawnY = commons().getSpawns().stream()
                .mapToDouble(PositionRotation::y)
                .min().orElse(70);
    }

    @Override
    protected void go() {
        Translations translations = gameHandle.getTranslations();
        var subject = translations.translateText(gameHandle.getGameInfo().getTaskKey());

        int duration = MIN_DURATION_SECONDS + random.nextInt(MAX_DURATION_SECONDS - MIN_DURATION_SECONDS + 1);
        commons().createTimer(subject, duration).whenDone(winManager::complete);

        gameHandle.getScheduler().interval(this::tick, 1);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            movementBlocker.enableMovement(player);
        }
    }

    private void setupTeam() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setCollisionRule(Team.CollisionRule.NEVER);
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        Translations translations = gameHandle.getTranslations();
        VisibilityHandler visibility = new VisibilityHandler(new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE), translations, gameHandle.getParticipants());
        visibility.init(gameHandle.getHooks());

        visibility.giveItems();
    }

    private void setupObjective() {
        var objective = gameHandle.getScoreboardManager().translateObjective("score", "game.ap2.chicken_shooter.points")
                .formatted(YELLOW, ChatFormatting.BOLD);

        useScoreboardStatsSync(data, objective);
        objective.setSlot(DisplaySlot.LIST);
        objective.setNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT);

        for (ServerPlayer player : PlayerLookup.all(gameHandle.getServer())) {
            objective.add(player);
        }
    }

    private void tick() {
        if (winManager.isGameOver()) return;

        ServerLevel world = getWorld();

        outer: for (ServerPlayer player : gameHandle.getParticipants()) {
            if (player.getY() >= minSpawnY - 1) continue;

            if (world.getFluidState(player.blockPosition()).is(FluidTags.WATER)) {
                onLandInWater(player);
                continue;
            }

            blocksBelow.clear();
            groundDetector.collectBlocksBelow(player, blocksBelow);

            for (BlockPos pos : blocksBelow) {
                BlockState state = world.getBlockState(pos);

                if (state.getCollisionShape(world, pos, CollisionContext.of(player)).isEmpty()) continue;

                onHitGround(player);

                continue outer;
            }
        }
    }

    private void onLandInWater(ServerPlayer player) {
        int count = removeWater(player.blockPosition());
        int score = (int) Math.round(Math.sqrt(count));

        score = Math.clamp(4 - score, 0, 3);

        commons().addScore(player, score, data);

        float pitch = switch (score) {
            case 2 -> 1.6f;
            case 3 -> 1.8f;
            default -> 1.4f;
        };

        gameHandle.getScheduler().immediate(() -> ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, pitch));

        commons().teleportToRandomSpawn(player, random);
    }

    private void onHitGround(ServerPlayer player) {
        commons().teleportToRandomSpawn(player, random);

        gameHandle.getScheduler().immediate(() -> ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.PLAYERS, 0.25f, 0.5f));
    }

    private int removeWater(BlockPos pos) {
        ServerLevel world = getWorld();
        var it = worldScanner.scan(pos);
        int count = 0;
        BlockState air = Blocks.AIR.defaultBlockState();

        while (it.hasNext()) {
            BlockPos waterPos = it.next();

            world.setBlockAndUpdate(waterPos, air);

            count++;
        }

        return count;
    }
}

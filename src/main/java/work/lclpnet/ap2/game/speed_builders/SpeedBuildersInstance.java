package work.lclpnet.ap2.game.speed_builders;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.projectile.BreezeWindChargeEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.game.speed_builders.data.SbModule;
import work.lclpnet.ap2.game.speed_builders.util.*;
import work.lclpnet.ap2.impl.game.Announcer;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.ParticleHelper;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.VelocityModifier;
import work.lclpnet.kibu.behaviour.world.ServerWorldBehaviour;
import work.lclpnet.kibu.hook.entity.ProjectileHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SpeedBuildersInstance extends EliminationGameInstance implements MapBootstrap {

    private static final int
            LOOK_DURATION_SECONDS = 10,
            JUDGE_DURATION_TICKS = Ticks.seconds(6),
            JUDGE_ANNOUNCEMENT_TICKS = Ticks.seconds(3),
            DESTROY_DELAY_TICKS = Ticks.seconds(4);
    private final Random random;
    private final SbSetup setup;
    private final SbItems items;
    private SbDestruction destruction = null;
    private SbManager manager = null;
    private SbIsland islandToDestroy = null;
    private UUID playerToEliminate = null;
    private BreezeEntity aelos = null;
    private BossBarTimer timer = null;
    private int timerTransaction = 0;

    public SpeedBuildersInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        random = new Random();
        setup = new SbSetup(random, gameHandle.getLogger());
        items = new SbItems();

        useSurvivalMode();
        disableTeleportEliminated();
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return setup.setup(map, world).thenRun(() -> {
            Participants participants = gameHandle.getParticipants();

            var islands = setup.createIslands(participants, world);

            aelos = setup.getAelos();

            manager = new SbManager(islands, setup.getModules(), gameHandle, world, random, this::allPlayersCompleted);
            destruction = new SbDestruction(world, random, aelos);
        });
    }

    @Override
    protected void prepare() {
        setupGameRules();

        ServerWorld world = getWorld();
        ServerWorldBehaviour.setFluidTicksEnabled(world, false);

        manager.eachIsland(SbIsland::teleport);

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        manager.setTeam(team);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PlayerAbilities abilities = player.getAbilities();
            abilities.allowFlying = true;
            abilities.flying = true;
            player.sendAbilitiesUpdate();

            scoreboardManager.joinTeam(player, team);
        }
    }

    @Override
    protected void ready() {
        SbConfiguration config = new SbConfiguration(gameHandle, manager, items);
        config.configureProtection();
        config.registerHooks();

        gameHandle.getHookRegistrar().registerHook(ProjectileHooks.HIT_BLOCK, this::onHitBlock);

        gameHandle.getGameScheduler().interval(manager::tick, 1);

        nextRound();
    }

    private void setupGameRules() {
        GameRules gameRules = getWorld().getGameRules();
        MinecraftServer server = gameHandle.getServer();

        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, server);
    }

    private void nextRound() {
        SbModule module = manager.nextModule();
        manager.reset();
        manager.setModule(module);
        items.setModule(module);

        commons().announcer().announceSubtitle("game.ap2.speed_builders.look");

        TranslationService translations = gameHandle.getTranslations();

        TranslatedText label = translations.translateText("game.ap2.speed_builders.prepare_label");
        timer = commons().createTimer(label, LOOK_DURATION_SECONDS, BossBar.Color.YELLOW);

        int transaction = timerTransaction;

        timer.whenDone(() -> {
            if (this.timerTransaction == transaction) {
                startBuilding();
            }
        });
    }

    private void startBuilding() {
        commons().announcer().announceSubtitle("game.ap2.speed_builders.copy");

        manager.setBuildingPhase(true);
        items.giveBuildingMaterials(gameHandle.getParticipants(), manager.getPreviewEntities());

        TranslationService translations = gameHandle.getTranslations();

        TranslatedText label = translations.translateText("game.ap2.speed_builders.label");
        timer = commons().createTimer(label, manager.getBuildingDurationTicks());

        int transaction = timerTransaction;

        timer.whenDone(() -> {
            if (this.timerTransaction == transaction) {
                onRoundOver();
            }
        });
    }

    private void onRoundOver() {
        if (manager.allIslandsComplete()) {
            allPlayersCompleted();
            return;
        }

        manager.resetSuccessiveCompletion();

        onLeaveBuildingPhase();

        Announcer announcer = commons().announcer();

        announcer.withTimes(5, 50, 0)
                .announce("game.ap2.speed_builders.time_up", null);

        TaskScheduler scheduler = gameHandle.getGameScheduler();

        scheduler.timeout(() -> announcer.silent()
                .withTimes(0, 35, 5)
                .announce("game.ap2.speed_builders.time_up", "game.ap2.speed_builders.grade"), 40);

        scheduler.timeout(this::announceJudgementDone, JUDGE_DURATION_TICKS);
    }

    private void onLeaveBuildingPhase() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            player.getInventory().clear();
        }

        manager.setBuildingPhase(false);
    }

    private void announceJudgementDone() {
        commons().announcer().announceSubtitle("game.ap2.speed_builders.judgement");

        gameHandle.getGameScheduler().timeout(this::announceJudgement, JUDGE_ANNOUNCEMENT_TICKS);
    }

    private void announceJudgement() {
        var worstPlayer = manager.getWorstPlayer();

        if (worstPlayer.isEmpty()) {
            winManager.win(getData().getBestSubject(resolver).orElse(null));
            return;
        }

        ServerPlayerEntity worst = worstPlayer.get();
        var title = Text.literal(worst.getNameForScoreboard()).formatted(Formatting.AQUA);
        var subtitle = gameHandle.getTranslations().translateText("game.ap2.speed_builders.will_eliminate").formatted(Formatting.DARK_GREEN);

        MinecraftServer server = gameHandle.getServer();

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            Title.get(player).title(title, subtitle.translateFor(player), 5, 50, 5);
            player.playSoundToPlayer(SoundEvents.ENTITY_BREEZE_HURT, SoundCategory.PLAYERS, 1f, 0.5f);
        }

        gameHandle.getGameScheduler().timeout(() -> fireChargeTowardsPlayerIsland(worst.getUuid()), DESTROY_DELAY_TICKS);
    }

    private void fireChargeTowardsPlayerIsland(UUID worstUuid) {
        ServerPlayerEntity worst = gameHandle.getServer().getPlayerManager().getPlayer(worstUuid);

        if (worst == null) {
            nextRoundOrGameOver();
            return;
        }

        var optIsland = manager.getIsland(worst);

        if (optIsland.isEmpty()) {
            eliminate(worst);
            nextRoundOrGameOver();
            return;
        }

        SbIsland island = optIsland.get();

        islandToDestroy = island;
        playerToEliminate = worstUuid;

        BreezeWindChargeEntity charge = destruction.fireProjectile(island);

        ServerWorld world = getWorld();

        gameHandle.getGameScheduler().interval(task -> {
            if (!charge.isAlive()) {
                task.cancel();
                return;
            }

            ParticleHelper.spawnForceParticle(ParticleTypes.FIREWORK, charge.getX(), charge.getY(), charge.getZ(), 50, 0, 0, 0, 0.25f, world.getPlayers());
        }, 1);

        // make sure the island is destroyed, if the projectile somehow misses ¯\_(ツ)_/¯
        gameHandle.getGameScheduler().timeout(() -> {
            if (charge.isAlive()) {
                charge.discard();
            }

            destroyIsland(null);
        }, Ticks.seconds(10));
    }

    private void onHitBlock(ProjectileEntity projectile, BlockHitResult hit) {
        if (!(projectile instanceof BreezeWindChargeEntity) || islandToDestroy == null) return;

        destroyIsland(projectile);
    }

    private void destroyIsland(@Nullable ProjectileEntity projectile) {
        if (islandToDestroy == null) return;  // the island was already destroyed

        Vec3d impactPos;
        Vec3d velocity;

        if (projectile != null) {
            impactPos = projectile.getPos();
            velocity = projectile.getVelocity().normalize();
        } else {
            impactPos = islandToDestroy.getCenter();
            velocity = impactPos.subtract(SbDestruction.getChargePos(aelos)).normalize();
        }

        destruction.destroyIsland(islandToDestroy, impactPos, velocity);

        islandToDestroy = null;

        PlayerManager playerManager = gameHandle.getServer().getPlayerManager();
        ServerPlayerEntity player = playerManager.getPlayer(playerToEliminate);

        if (player == null) {
            nextRoundOrGameOver();
            return;
        }

        player.getAbilities().flying = false;
        player.getAbilities().allowFlying = false;
        player.sendAbilitiesUpdate();

        VelocityModifier.setVelocity(player, velocity.add(0, 1.5, 0).normalize().multiply(3));

        gameHandle.getGameScheduler().timeout(() -> {
            ServerPlayerEntity futurePlayer = playerManager.getPlayer(playerToEliminate);

            if (futurePlayer != null) {
                eliminate(futurePlayer);
            }

            nextRoundOrGameOver();
        }, Ticks.seconds(2));
    }

    private void nextRoundOrGameOver() {
        if (winManager.isGameOver()) return;

        islandToDestroy = null;
        playerToEliminate = null;

        if (gameHandle.getParticipants().count() > 1) {
            nextRound();
            return;
        }

        var it = gameHandle.getParticipants().iterator();

        if (it.hasNext()) {
            winManager.win(it.next());
        } else {
            winManager.winNobody();
        }
    }

    private void allPlayersCompleted() {
        manager.incrementSuccessiveCompletion();

        timerTransaction++;

        if (timer != null) {
            timer.stop();
        }

        onLeaveBuildingPhase();

        commons().announcer()
                .withSound(SoundEvents.ENTITY_BREEZE_IDLE_AIR, SoundCategory.HOSTILE, 1f, 1.2f)
                .announceSubtitle("game.ap2.speed_builders.impressed");

        gameHandle.getGameScheduler().timeout(this::nextRoundOrGameOver, Ticks.seconds(3));
    }
}

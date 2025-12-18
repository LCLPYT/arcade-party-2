package work.lclpnet.ap2.game.speed_builders;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.NotNull;
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
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SpeedBuildersInstance extends EliminationGameInstance implements MapBootstrap {

    private static final int
            LOOK_DURATION_SECONDS = 8,
            JUDGE_DURATION_TICKS = Ticks.seconds(5),
            JUDGE_ANNOUNCEMENT_TICKS = Ticks.seconds(3),
            DESTROY_DELAY_TICKS = Ticks.seconds(4);

    private final Random random;
    private final SbSetup setup;
    private final SbItems items;
    private SbDestruction destruction = null;
    private SbManager manager = null;
    private SbIsland islandToDestroy = null;
    private UUID playerToEliminate = null;
    private UUID aelosId = null;
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
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map) {
        return setup.setup(map, world).thenRun(() -> {
            Participants participants = gameHandle.getParticipants();

            var islands = setup.createIslands(participants, world);

            aelosId = setup.getAelosId();

            manager = new SbManager(islands, setup.getModules(), gameHandle, world, random, this::allPlayersCompleted);
            destruction = new SbDestruction(world, random, aelosId);

            world.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true, gameHandle.getServer());
        });
    }

    @Override
    protected void prepare() {
        setupGameRules();

        ServerLevel world = getWorld();
        ServerWorldBehaviour.setFluidTicksEnabled(world, false);

        manager.eachIsland(SbIsland::teleport);

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setCollisionRule(Team.CollisionRule.NEVER);
        manager.setTeam(team);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            Abilities abilities = player.getAbilities();
            abilities.mayfly = true;
            abilities.flying = true;
            player.onUpdateAbilities();

            scoreboardManager.joinTeam(player, team);
        }

        gameHandle.getRootScheduler().interval(manager::tick, 1);
    }

    @Override
    protected void go() {
        SbConfiguration config = new SbConfiguration(gameHandle, manager, items);
        config.configureProtection();
        config.registerHooks();

        gameHandle.getHooks().registerHook(ProjectileHooks.HIT_BLOCK, this::onHitBlock);

        nextRound();
    }

    @Override
    protected void onEliminated(ServerPlayer player) {
        putScoreDetail(player, false);
    }

    @Override
    public void participantRemoved(ServerPlayer player) {
        Participants participants = gameHandle.getParticipants();

        if (participants.count() == 1) {
            participants.stream().findAny().ifPresent(winner -> putScoreDetail(winner, true));
        }

        super.participantRemoved(player);
    }

    private void setupGameRules() {
        commons().gameRuleBuilder()
                .set(GameRules.RULE_RANDOMTICKING, 0);
    }

    private void nextRound() {
        SbModule module = manager.nextModule();
        manager.reset();
        manager.setModule(module);
        items.setModule(module);

        commons().announcer().announceSubtitle("game.ap2.speed_builders.look");

        Translations translations = gameHandle.getTranslations();

        TranslatedText label = translations.translateText("game.ap2.speed_builders.prepare_label");
        timer = commons().createTimer(label, LOOK_DURATION_SECONDS, BossEvent.BossBarColor.YELLOW);

        int transaction = timerTransaction;

        timer.whenDone(() -> {
            if (this.timerTransaction == transaction) {
                startBuilding();
            }
        });
    }

    private void startBuilding() {
        commons().announcer().announceSubtitle("game.ap2.speed_builders.copy");

        var entities = manager.getPreviewEntities();

        manager.clearIslands();
        manager.setBuildingPhase(true);
        items.giveBuildingMaterials(gameHandle.getParticipants(), entities);

        Translations translations = gameHandle.getTranslations();

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

        TaskScheduler scheduler = gameHandle.getScheduler();

        scheduler.timeout(() -> announcer.silent()
                .withTimes(0, 35, 5)
                .announce("game.ap2.speed_builders.time_up", "game.ap2.speed_builders.grade"), 40);

        scheduler.timeout(this::announceJudgementDone, JUDGE_DURATION_TICKS);
    }

    private void onLeaveBuildingPhase() {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            player.getInventory().clearContent();
        }

        manager.setBuildingPhase(false);
    }

    private void announceJudgementDone() {
        commons().announcer().announceSubtitle("game.ap2.speed_builders.judgement");

        gameHandle.getScheduler().timeout(this::announceJudgement, JUDGE_ANNOUNCEMENT_TICKS);
    }

    private void announceJudgement() {
        var worstPlayer = manager.getWorstPlayer();

        if (worstPlayer.isEmpty()) {
            winManager.complete();
            return;
        }

        ServerPlayer worst = worstPlayer.get();
        var title = Component.literal(worst.getScoreboardName()).withStyle(ChatFormatting.AQUA);
        var subtitle = gameHandle.getTranslations().translateText("game.ap2.speed_builders.will_eliminate").formatted(ChatFormatting.DARK_GREEN);

        MinecraftServer server = gameHandle.getServer();

        for (ServerPlayer player : PlayerLookup.all(server)) {
            Title.get(player).title(title, subtitle.translateFor(player), 5, 50, 5);
            player.playNotifySound(SoundEvents.BREEZE_HURT, SoundSource.PLAYERS, 1f, 0.5f);
        }

        manager.getIsland(worst).ifPresent(destruction::setAelosLookingTowards);

        gameHandle.getScheduler().timeout(() -> fireChargeTowardsPlayerIsland(worst.getUUID()), DESTROY_DELAY_TICKS);
    }

    private void fireChargeTowardsPlayerIsland(UUID worstUuid) {
        ServerPlayer worst = gameHandle.getServer().getPlayerList().getPlayer(worstUuid);

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

        BreezeWindCharge charge = destruction.fireProjectile(island);

        ServerLevel world = getWorld();

        gameHandle.getScheduler().interval(task -> {
            if (!charge.isAlive()) {
                task.cancel();
                return;
            }

            ParticleHelper.spawnForceParticle(ParticleTypes.FIREWORK, charge.getX(), charge.getY(), charge.getZ(), 50, 0, 0, 0, 0.25f, world.players());
        }, 1);

        // make sure the island is destroyed, if the projectile somehow misses ¯\_(ツ)_/¯
        gameHandle.getScheduler().timeout(() -> {
            if (charge.isAlive()) {
                charge.discard();
            }

            destroyIsland(null);
        }, Ticks.seconds(10));
    }

    private void putScoreDetail(ServerPlayer player, boolean winner) {
        int completed = manager.getRoundsCompleted(player, winner);
        var detail = gameHandle.getTranslations().translateText("game.ap2.speed_builders.survived", completed);

        getData().add(player, detail);
    }

    private void onHitBlock(Projectile projectile, BlockHitResult hit) {
        if (!(projectile instanceof BreezeWindCharge) || islandToDestroy == null) return;

        destroyIsland(projectile);
    }

    private void destroyIsland(@Nullable Projectile projectile) {
        if (islandToDestroy == null) return;  // the island was already destroyed

        Vec3 impactPos;
        Vec3 velocity;

        if (projectile != null) {
            impactPos = projectile.position();
            velocity = projectile.getDeltaMovement().normalize();
        } else {
            impactPos = islandToDestroy.getCenter();
            Entity entity = getWorld().getEntity(aelosId);

            if (entity instanceof Breeze breeze) {
                velocity = impactPos.subtract(SbDestruction.getChargePos(breeze)).normalize();
            } else {
                velocity = impactPos.normalize();
            }
        }

        destruction.destroyIsland(islandToDestroy, impactPos, velocity);

        islandToDestroy = null;

        PlayerList playerManager = gameHandle.getServer().getPlayerList();
        ServerPlayer player = playerManager.getPlayer(playerToEliminate);

        if (player == null) {
            nextRoundOrGameOver();
            return;
        }

        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;
        player.onUpdateAbilities();

        VelocityModifier.setVelocity(player, velocity.add(0, 1.5, 0).normalize().scale(3));

        gameHandle.getScheduler().timeout(() -> {
            ServerPlayer futurePlayer = playerManager.getPlayer(playerToEliminate);

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
            manager.incrementRound();
            nextRound();
            return;
        }

        var it = gameHandle.getParticipants().iterator();

        if (!it.hasNext()) {
            winManager.cancel();
            return;
        }

        ServerPlayer winner = it.next();

        putScoreDetail(winner, true);

        winManager.complete();
    }

    private void allPlayersCompleted() {
        manager.incrementSuccessiveCompletion();

        timerTransaction++;

        if (timer != null) {
            timer.stop();
        }

        onLeaveBuildingPhase();

        commons().announcer()
                .withSound(SoundEvents.BREEZE_IDLE_AIR, SoundSource.HOSTILE, 1f, 1.2f)
                .announceSubtitle("game.ap2.speed_builders.impressed");

        gameHandle.getScheduler().timeout(this::nextRoundOrGameOver, Ticks.seconds(3));
    }
}

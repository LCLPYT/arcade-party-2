package work.lclpnet.ap2.game.speed_builders;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.behaviour.world.ServerWorldBehaviour;
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

    private static final int BUILD_DURATION_SECONDS = 30;
    private static final int
            LOOK_DURATION_TICKS = Ticks.seconds(10),
            JUDGE_DURATION_TICKS = Ticks.seconds(6),
            JUDGE_ANNOUNCEMENT_TICKS = Ticks.seconds(3),
            DESTROY_DELAY_TICKS = Ticks.seconds(3);
    private final Random random;
    private final SbSetup setup;
    private final SbItems items;
    private SbDestruction destruction = null;
    private SbManager manager = null;

    public SpeedBuildersInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        random = new Random();
        setup = new SbSetup(random, gameHandle.getLogger());
        items = new SbItems();

        useSurvivalMode();
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return setup.setup(map, world).thenRun(() -> {
            Participants participants = gameHandle.getParticipants();

            var islands = setup.createIslands(participants, world);

            manager = new SbManager(islands, setup.getModules(), gameHandle, world, random);
            destruction = new SbDestruction(world);
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

        nextRound();
    }

    private void setupGameRules() {
        GameRules gameRules = getWorld().getGameRules();
        MinecraftServer server = gameHandle.getServer();

        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, server);
    }

    private void nextRound() {
        SbModule module = manager.nextModule();
        manager.setModule(module);
        items.setModule(module);

        commons().announcer().announceSubtitle("game.ap2.speed_builders.look");

        gameHandle.getGameScheduler().timeout(this::startBuilding, LOOK_DURATION_TICKS);
    }

    private void startBuilding() {
        commons().announcer().announceSubtitle("game.ap2.speed_builders.copy");

        manager.setBuildingPhase(true);
        items.giveBuildingMaterials(gameHandle.getParticipants(), manager.getPreviewEntities());

        TranslationService translations = gameHandle.getTranslations();

        TranslatedText label = translations.translateText("game.ap2.speed_builders.label");
        BossBarTimer timer = commons().createTimer(label, BUILD_DURATION_SECONDS);

        timer.whenDone(this::onRoundOver);
    }

    private void onRoundOver() {
        Announcer announcer = commons().announcer();
        announcer.announce("game.ap2.speed_builders.time_up", null, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 1, 1.2f);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            player.getInventory().clear();
        }

        manager.setBuildingPhase(false);

        TaskScheduler scheduler = gameHandle.getGameScheduler();
        scheduler.timeout(() -> {
            announcer.announceWithoutSound("game.ap2.speed_builders.time_up", "game.ap2.speed_builders.grade", 0, 30, 5);

            for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
                player.playSound(SoundEvents.ENTITY_BREEZE_INHALE, SoundCategory.AMBIENT, 1, 1.2f);
            }
        }, 40);
        scheduler.timeout(this::announceJudgementDone, JUDGE_DURATION_TICKS);
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
            Title.get(player).title(title, subtitle.translateFor(player), 5, 30, 5);
            player.playSound(SoundEvents.ENTITY_BREEZE_HURT, SoundCategory.PLAYERS, 1f, 0.5f);
        }

        gameHandle.getGameScheduler().timeout(() -> destroyIsland(worst.getUuid()), DESTROY_DELAY_TICKS);
    }

    private void destroyIsland(@Nullable UUID worstUuid) {
        ServerPlayerEntity worst = gameHandle.getServer().getPlayerManager().getPlayer(worstUuid);

        if (worst != null) {
            manager.getIsland(worst).ifPresent(destruction::destroyIsland);
            eliminate(worst);

            if (winManager.isGameOver()) return;
        }

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
}

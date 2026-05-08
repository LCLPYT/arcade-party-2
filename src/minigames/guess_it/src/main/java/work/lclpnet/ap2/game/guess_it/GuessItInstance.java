package work.lclpnet.ap2.game.guess_it;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.core.hook.CopperGolemTurnIntoStatueCallback;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.game.guess_it.util.AnswerCommand;
import work.lclpnet.ap2.game.guess_it.util.DynamicEntityModifier;
import work.lclpnet.ap2.game.guess_it.util.SetChallengeCommand;
import work.lclpnet.ap2.game.guess_it.util.SkipChallengeCommand;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.ScoreboardUtil;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreHandle;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.ds.IndexedSet;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.*;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.lobby.util.ResetWorldModifier;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class GuessItInstance extends FFAGameInstance implements MapBootstrap {

    private static final int PREPARATION_TICKS = Ticks.seconds(3);
    private static final int DELAY_TICKS = Ticks.seconds(5);
    private static final int MIN_ROUNDS = 8, MAX_ROUNDS = 14;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;
    private final IntScoreDataContainer<ServerPlayer, PlayerRef> data = new IntScoreDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final PlayerChoices choices;
    private final ChallengeResult result;
    private ChallengeMessengerImpl messenger;
    private InputManager inputManager;
    private GuessItManager manager = null;
    private Challenge challenge = null;
    private SoundSubtitles soundSubtitles = null;
    private IndexedSet<UUID> mannequinUuids = null;
    private ResetWorldModifier modifier = null;
    private DynamicEntityModifier dynamicEntities = null;
    private ScoreHandle roundHandle = null;
    private int round = 0;
    private int rounds = 10;
    private int consecutiveErrors = 0;
    private TaskHandle currentTask;
    private BossBarTimer timer;
    private int transaction = 0;

    public GuessItInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        choices = new PlayerChoices(gameHandle.getTranslations());
        result = new ChallengeResult();
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map) {
        var soundSubtitlesFuture = SoundSubtitles.load().thenAccept(sub -> soundSubtitles = sub);
        var mannequinUuidsFuture = loadMannequinUuids().thenAccept(ids -> mannequinUuids = new IndexedSet<>(ids));

        return CompletableFuture.allOf(soundSubtitlesFuture, mannequinUuidsFuture);
    }

    @Override
    protected void prepare() {
        ServerLevel world = getWorld();
        GameMap map = getMap();
        HookRegistrar hooks = gameHandle.getHooks();
        Participants participants = gameHandle.getParticipants();
        BlockShape blockShape = MapUtil.readArea(map);

        commons().gameRuleBuilder()
                .set(GameRules.REDUCED_DEBUG_INFO, true);

        rounds = MIN_ROUNDS + random.nextInt(MAX_ROUNDS - MIN_ROUNDS + 1);

        messenger = new ChallengeMessengerImpl(world, gameHandle.getTranslations());
        inputManager = new InputManager(choices, gameHandle.getTranslations(), participants, messenger);
        modifier = new ResetWorldModifier(world, hooks);

        var dynamicEntityManager = new DynamicEntityManager(world);
        dynamicEntityManager.init(gameHandle.getRootScheduler(), hooks);
        dynamicEntities = new DynamicEntityModifier(dynamicEntityManager);

        manager = new GuessItManager(gameHandle, world, random, blockShape, modifier, soundSubtitles,
                commons().debugController(), mannequinUuids, dynamicEntities);

        CommandRegistrar commands = gameHandle.getCommands();

        new AnswerCommand(participants, inputManager).register(commands);
        new SetChallengeCommand(manager, this::skip).register(commands);
        new SkipChallengeCommand(this::skip).register(commands);

        setupScoreboard();

        // ignore daylight affection for undead mobs
        AffectedByDaylightCallback.HOOK.registerWith(hooks, _ -> true);

        // prevent entity conversion, e.g. piglin -> zombified piglin
        EntityConvertCallback.HOOK.registerWith(hooks, (_, _) -> true);

        // prevent entity teleportation
        EntityTeleportCallback.HOOK.registerWith(hooks, (_, _, _, _) -> true);

        // prevent entity targeting
        EntityTargetCallback.HOOK.registerWith(hooks, (_, _) -> true);

        // prevent mobs from applying effects to players
        EntityStatusEffectCallback.HOOK.registerWith(hooks, (entity, _, source) -> entity instanceof ServerPlayer && source != null);

        // prevent wither shooting skulls
        WitherShootCallback.HOOK.registerWith(hooks, (_, _, _, _) -> true);

        // prevent boss mobs from creating boss bars for players
        EntityBossBarCallback.HOOK.registerWith(hooks, (_, _, _) -> true);

        // prevent copper golems from turning into statues and leaving blocks
        CopperGolemTurnIntoStatueCallback.HOOK.registerWith(hooks, _ -> true);

        commons().teleportToRandomSpawns(random);
    }

    @Override
    protected void go() {
        inputManager.init(gameHandle.getHooks());

        prepareNextChallenge();
    }

    private void setupScoreboard() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        Translations translations = gameHandle.getTranslations();

        var objective = ScoreboardUtil.setupSidebar(scoreboardManager, gameHandle.getGameInfo().getTitleKey());

        // round display
        roundHandle = objective.createText(translations.translateText("game.ap2.guess_it.round").formatted(GREEN));
        updateRoundDisplay();

        objective.createNewline(ScoreboardLayout.TOP);

        // score heading
        objective.createText(translations.translateText("ap2.score").formatted(YELLOW, BOLD));

        var separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR_SM).withStyle(DARK_GREEN, STRIKETHROUGH);
        objective.createText(separator);

        for (ServerPlayer player : PlayerLookup.all(gameHandle.getServer())) {
            objective.add(player);
        }

        useScoreboardStatsSync(data, objective);
    }

    private void updateRoundDisplay() {
        roundHandle.setNumberFormat(new FixedFormat(Component.literal("%s/%s".formatted(round, rounds)).withStyle(YELLOW)));
    }

    private synchronized void prepareNextChallenge() {
        modifier.undo();
        dynamicEntities.reset();

        if (challenge != null) {
            try {
                challenge.destroy();
            } catch (Throwable t) {
                gameHandle.getLogger().error("Failed to destroy {}, ignoring it", challenge.getClass().getSimpleName(), t);
            }
        }

        round++;
        updateRoundDisplay();

        var challengeInit = manager.nextChallenge();

        challenge = challengeInit.challenge();
        ServerLevel world = getWorld();
        Translations translations = gameHandle.getTranslations();

        var prepareMsg = translations.translateText("game.ap2.guess_it.prepare." + challenge.getPreparationKey())
                .formatted(DARK_GREEN, BOLD);

        challenge.init(challengeInit.init());

        // send preparation title
        for (ServerPlayer player : PlayerLookup.level(world)) {
            Title.get(player).title(Component.empty(), prepareMsg.translateFor(player));
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.NEUTRAL, 1f, 0.5f);
        }

        try {
            challenge.prepare();
        } catch (Throwable t) {
            gameHandle.getLogger().error("Failed to prepare {}", challenge.getClass().getSimpleName(), t);
            onChallengeError();
            return;
        }

        int expected = ++transaction;

        currentTask = gameHandle.getScheduler().timeout(() -> {
            if (transaction != expected) return;

            beginChallenge();
        }, PREPARATION_TICKS);
    }

    private synchronized void beginChallenge() {
        Objects.requireNonNull(challenge, "Challenge cannot be null");

        ServerLevel world = getWorld();
        Translations translations = gameHandle.getTranslations();
        TaskScheduler scheduler = gameHandle.getScheduler();

        var players = PlayerLookup.level(world);

        if (challenge.shouldPlayBeginSound()) {
            for (ServerPlayer player : players) {
                ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 1f, 0.5f);
            }
        }

        messenger.reset();

        try {
            challenge.begin(inputManager, messenger);
        } catch (Throwable t) {
            gameHandle.getLogger().error("Failed to begin {}", challenge.getClass().getSimpleName(), t);
            onChallengeError();
            return;
        }

        consecutiveErrors = 0;

        messenger.send();

        int durationTicks = challenge.getDurationTicks();

        timer = BossBarTimer.builder(translations, translations.translateText("game.ap2.guess_it.answer"))
                .withAlertSound(true)
                .withColor(BossEvent.BossBarColor.RED)
                .withDurationTicks(durationTicks)
                .build();

        timer.addPlayers(players);

        int expected = ++transaction;

        timer.whenDone(() -> {
            if (transaction != expected) return;

            onTimerOver();
        });

        timer.start(gameHandle.getBossBarProvider(), scheduler);
    }

    private void onChallengeError() {
        consecutiveErrors++;

        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            // to many errors in a row, abort the game
            winManager.complete();
            return;
        }

        round--;
        prepareNextChallenge();
    }

    private synchronized void onTimerOver() {
        if (challenge instanceof LongerChallenge longerChallenge) {
            inputManager.setLocked(true);
            longerChallenge.evaluateDeferred(this::evaluateChallenge);
            return;
        }

        this.evaluateChallenge();
    }

    private synchronized void evaluateChallenge() {
        Objects.requireNonNull(challenge, "Challenge cannot be null");

        Translations translations = gameHandle.getTranslations();

        result.clear();
        challenge.evaluate(choices, result);

        Object correctAnswer = result.getCorrectAnswer();
        TranslatedText solutionMsg = null;

        if (correctAnswer != null) {
            solutionMsg = translations.translateText("game.ap2.guess_it.solution", styled(correctAnswer, YELLOW));
        }

        for (ServerPlayer player : gameHandle.getParticipants()) {
            int points = result.getPointsGained(player);

            var msg = translations.translateText(player, "game.ap2.guess_it.gain_points", styled(points, YELLOW)).formatted(GREEN);

            player.sendOverlayMessage(msg);

            if (points > 0) {
                data.addScore(player, points);

                ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.5f);

                if (solutionMsg != null) {
                    player.sendSystemMessage(solutionMsg.translateFor(player).formatted(GREEN));
                }

                continue;
            }

            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.3f, 1.3f);

            if (solutionMsg != null) {
                player.sendSystemMessage(solutionMsg.translateFor(player).formatted(RED));
            }
        }

        inputManager.reset();

        if (round >= rounds) {
            winManager.complete();
            return;
        }

        int expected = ++transaction;

        currentTask = gameHandle.getScheduler().timeout(() -> {
            if (transaction != expected) return;

            prepareNextChallenge();
        }, DELAY_TICKS);
    }

    private synchronized void skip() {
        transaction++;

        if (currentTask != null) {
            currentTask.cancel();
        }

        if (timer != null) {
            timer.stop();
        }

        messenger.reset();
        inputManager.reset();
        result.clear();

        round--;
        prepareNextChallenge();
    }

    private CompletableFuture<Set<UUID>> loadMannequinUuids() {
        return CompletableFuture.supplyAsync(this::loadMannequinUuidsSync);
    }

    private Set<UUID> loadMannequinUuidsSync() {
        var in = getClass().getResourceAsStream("/mannequin_players.json");

        if (in == null) return Set.of();

        try (in) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            JSONArray array = json.getJSONArray("uuids");

            Set<UUID> uuids = new HashSet<>(array.length());

            for (Object uuid : array) {
                if (!(uuid instanceof String str)) continue;

                try {
                    uuids.add(UUID.fromString(str));
                } catch (IllegalArgumentException e) {
                    gameHandle.getLogger().error("Malformed uuid: {}", str, e);
                }
            }

            return uuids;
        } catch (Throwable t) {
            gameHandle.getLogger().error("Failed to load mannequin player uuids", t);
            return Set.of();
        }
    }
}

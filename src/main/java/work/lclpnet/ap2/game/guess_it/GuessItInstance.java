package work.lclpnet.ap2.game.guess_it;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreHandle;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.kibu.hook.entity.*;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.lobby.util.ResetWorldModifier;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class GuessItInstance extends DefaultGameInstance implements MapBootstrap {

    private static final int PREPARATION_TICKS = Ticks.seconds(3);
    private static final int DELAY_TICKS = Ticks.seconds(5);
    private static final int MIN_ROUNDS = 8, MAX_ROUNDS = 14;
    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final PlayerChoices choices;
    private final ChallengeResult result;
    private ChallengeMessengerImpl messenger;
    private InputManager inputManager;
    private GuessItManager manager = null;
    private Challenge challenge = null;
    private SoundSubtitles soundSubtitles = null;
    private ResetWorldModifier modifier = null;
    private ScoreHandle roundHandle = null;
    private int round = 0;
    private int rounds = 10;

    public GuessItInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        choices = new PlayerChoices(gameHandle.getTranslations());
        result = new ChallengeResult();
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();
        Stage stage = readStage();

        rounds = MIN_ROUNDS + random.nextInt(MAX_ROUNDS - MIN_ROUNDS + 1);

        messenger = new ChallengeMessengerImpl(world, gameHandle.getTranslations());
        inputManager = new InputManager(choices, gameHandle.getTranslations(), participants, messenger);
        modifier = new ResetWorldModifier(world, hooks);
        manager = new GuessItManager(gameHandle, world, random, stage, modifier, soundSubtitles);

        new AnswerCommand(participants, inputManager).register(gameHandle.getCommandRegistrar());

        setupScoreboard();

        // ignore daylight affection for undead mobs
        hooks.registerHook(AffectedByDaylightCallback.HOOK, entity -> true);

        // prevent entity conversion, e.g. piglin -> zombified piglin
        hooks.registerHook(EntityConvertCallback.HOOK, (entity, type) -> true);

        // prevent entity teleportation
        hooks.registerHook(EntityTeleportCallback.HOOK, (entity, x, y, z) -> true);

        // prevent entity targeting
        hooks.registerHook(EntityTargetCallback.HOOK, (entity, target) -> true);

        // prevent mobs from applying effects to players
        hooks.registerHook(EntityStatusEffectCallback.HOOK, (entity, effect, source) -> entity instanceof ServerPlayerEntity && source != null);

        // prevent wither shooting skulls
        hooks.registerHook(WitherShootCallback.HOOK, (wither, targetX, targetY, targetZ) -> true);

        // prevent boss mobs from creating boss bars for players
        hooks.registerHook(EntityBossBarCallback.HOOK, (entity, bossBar, player) -> true);

        teleportRandom();
    }

    @Override
    protected void ready() {
        inputManager.init(gameHandle.getHookRegistrar());

        prepareNextChallenge();
    }

    private void teleportRandom() {
        List<PositionRotation> spawns = MapUtils.getSpawnPositionsAndRotation(getMap());

        if (spawns.isEmpty()) return;

        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PositionRotation spawn = spawns.get(random.nextInt(spawns.size()));
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());
        }
    }

    private void setupScoreboard() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        TranslationService translations = gameHandle.getTranslations();

        var objective = scoreboardManager.translateObjective("score", gameHandle.getGameInfo().getTitleKey())
                .formatted(AQUA, Formatting.BOLD);

        objective.setSlot(ScoreboardDisplaySlot.SIDEBAR);
        objective.setDisplayName(holder -> Text.literal(holder).formatted(GREEN));
        objective.setNumberFormat(StyledNumberFormat.YELLOW);

        // separators at top and bottom
        var separator = Text.literal(ApConstants.SCOREBOARD_SEPARATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);
        objective.createText(separator, ScoreboardLayout.TOP);
        objective.createText(separator, ScoreboardLayout.BOTTOM);

        // round display
        roundHandle = objective.createText(translations.translateText("game.ap2.guess_it.round").formatted(GREEN));
        updateRoundDisplay();

        objective.createNewline(ScoreboardLayout.TOP);

        // score heading
        objective.createText(translations.translateText("ap2.score").formatted(YELLOW, BOLD));

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            objective.addPlayer(player);
        }

        useScoreboardStatsSync(data, objective);
    }

    private void updateRoundDisplay() {
        roundHandle.setNumberFormat(new FixedNumberFormat(Text.literal("%s/%s".formatted(round, rounds)).formatted(YELLOW)));
    }

    private Stage readStage() {
        JSONObject area = getMap().requireProperty("area");
        String type = area.getString("type").toLowerCase(Locale.ROOT);

        if (type.equals(CylinderStage.TYPE)) {
            BlockPos origin = MapUtil.readBlockPos(area.getJSONArray("origin"));
            int radius = area.getInt("radius");
            int height = area.getInt("height");
            return new CylinderStage(origin, radius, height);
        }

        throw new IllegalStateException("Unknown area type " + type);
    }

    private void prepareNextChallenge() {
        modifier.undo();

        if (challenge != null) {
            challenge.destroy();
        }

        round++;
        updateRoundDisplay();

        challenge = manager.nextChallenge();
        ServerWorld world = getWorld();
        TranslationService translations = gameHandle.getTranslations();

        var prepareMsg = translations.translateText("game.ap2.guess_it.prepare." + challenge.getPreparationKey())
                .formatted(DARK_GREEN, BOLD);

        // send preparation title
        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            Title.get(player).title(Text.empty(), prepareMsg.translateFor(player));
            player.playSound(SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.NEUTRAL, 1f, 0.5f);
        }

        challenge.prepare();

        gameHandle.getGameScheduler().timeout(this::beginChallenge, PREPARATION_TICKS);
    }

    private void beginChallenge() {
        Objects.requireNonNull(challenge, "Challenge cannot be null");

        ServerWorld world = getWorld();
        TranslationService translations = gameHandle.getTranslations();
        TaskScheduler scheduler = gameHandle.getGameScheduler();

        var players = PlayerLookup.world(world);

        for (ServerPlayerEntity player : players) {
            player.playSound(SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.NEUTRAL, 1f, 0.5f);
        }

        messenger.reset();
        challenge.begin(inputManager, messenger);
        messenger.send();

        int durationTicks = challenge.getDurationTicks();

        BossBarTimer timer = BossBarTimer.builder(translations, translations.translateText("game.ap2.guess_it.answer"))
                .withAlertSound(true)
                .withColor(BossBar.Color.RED)
                .withDurationTicks(durationTicks)
                .build();

        timer.addPlayers(players);
        timer.whenDone(this::onTimerOver);
        timer.start(gameHandle.getBossBarProvider(), scheduler);
    }

    private void onTimerOver() {
        if (challenge instanceof LongerChallenge longerChallenge) {
            inputManager.setLocked(true);
            longerChallenge.evaluateDeferred(this::evaluateChallenge);
            return;
        }

        this.evaluateChallenge();
    }

    private void evaluateChallenge() {
        Objects.requireNonNull(challenge, "Challenge cannot be null");

        TranslationService translations = gameHandle.getTranslations();

        result.clear();
        challenge.evaluate(choices, result);

        Object correctAnswer = result.getCorrectAnswer();
        TranslatedText solutionMsg = null;

        if (correctAnswer != null) {
            solutionMsg = translations.translateText("game.ap2.guess_it.solution", styled(correctAnswer, YELLOW));
        }

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            int points = result.getPointsGained(player);

            var msg = translations.translateText(player, "game.ap2.guess_it.gain_points", styled(points, YELLOW)).formatted(GREEN);

            player.sendMessage(msg, true);

            if (points > 0) {
                data.addScore(player, points);

                player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.5f);

                if (solutionMsg != null) {
                    player.sendMessage(solutionMsg.translateFor(player).formatted(GREEN));
                }

                continue;
            }

            player.playSound(SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.3f, 1.3f);

            if (solutionMsg != null) {
                player.sendMessage(solutionMsg.translateFor(player).formatted(RED));
            }
        }

        inputManager.reset();

        if (round >= rounds) {
            winManager.win(data.getBestSubjects(resolver));
        } else {
            gameHandle.getGameScheduler().timeout(this::prepareNextChallenge, DELAY_TICKS);
        }
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return SoundSubtitles.load().thenAccept(sub -> soundSubtitles = sub);
    }
}

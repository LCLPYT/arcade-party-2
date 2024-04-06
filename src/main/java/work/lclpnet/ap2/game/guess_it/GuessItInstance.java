package work.lclpnet.ap2.game.guess_it;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.json.JSONObject;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.game.guess_it.data.*;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.kibu.hook.entity.*;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.lobby.util.ResetWorldModifier;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class GuessItInstance extends DefaultGameInstance {

    private static final int PREPARATION_TICKS = Ticks.seconds(3);
    private static final int DELAY_TICKS = Ticks.seconds(5);
    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreDataContainer<>(PlayerRef::create);
    private final PlayerChoices choices;
    private final InputManager inputManager;
    private final ChallengeResult result;
    private GuessItManager manager = null;
    private Challenge challenge = null;
    private ResetWorldModifier modifier;

    public GuessItInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        choices = new PlayerChoices();
        inputManager = new InputManager(choices, gameHandle.getTranslations(), gameHandle.getParticipants());
        result = new ChallengeResult();
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();
        Random random = new Random();

        Stage stage = readStage();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        modifier = new ResetWorldModifier(world, hooks);
        manager = new GuessItManager(gameHandle, world, random, stage, modifier);

        // make participants invulnerable, so that they won't be targeted by mobs
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            player.getAbilities().invulnerable = true;
            player.sendAbilitiesUpdate();
        }

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
    }

    @Override
    protected void ready() {
        inputManager.init(gameHandle.getHookRegistrar());

        prepareNextChallenge();
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

        challenge.begin(inputManager);

        int durationTicks = challenge.getDurationTicks();

        BossBarTimer timer = BossBarTimer.builder(translations, translations.translateText("game.ap2.guess_it.answer"))
                .withAlertSound(true)
                .withColor(BossBar.Color.RED)
                .withDurationTicks(durationTicks)
                .build();

        timer.addPlayers(players);
        timer.whenDone(this::evaluateChallenge);
        timer.start(gameHandle.getBossBarProvider(), scheduler);
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

        gameHandle.getGameScheduler().timeout(this::prepareNextChallenge, DELAY_TICKS);
    }
}

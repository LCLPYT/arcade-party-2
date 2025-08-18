package work.lclpnet.ap2.base.activity;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.activity.Activity;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BossBarComponent;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.game.GameStartContext;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.util.music.SongWrapper;
import work.lclpnet.ap2.api.util.music.WeightedSong;
import work.lclpnet.ap2.base.ApMiniGameArgs;
import work.lclpnet.ap2.base.api.Skippable;
import work.lclpnet.ap2.base.cmd.ForceMapCommand;
import work.lclpnet.ap2.base.cmd.SkipCommand;
import work.lclpnet.ap2.base.util.ApBaseArgs;
import work.lclpnet.ap2.base.util.BaseActivityConfigurator;
import work.lclpnet.ap2.base.util.OptionChooser;
import work.lclpnet.ap2.base.util.ScoreManager;
import work.lclpnet.ap2.impl.activity.ArcadePartyComponents;
import work.lclpnet.ap2.impl.activity.ScoreboardComponent;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.scene.MixedMountContext;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.ap2.impl.scene.Scene;
import work.lclpnet.ap2.impl.scene.object.TranslatedTextDisplayObject;
import work.lclpnet.ap2.impl.util.IconMaker;
import work.lclpnet.ap2.impl.util.ScoreboardUtil;
import work.lclpnet.ap2.impl.util.music.MusicHelper;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreboardObjective;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.ap2.impl.util.title.AnimatedTitle;
import work.lclpnet.ap2.impl.util.title.NextGameTitleAnimation;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntityManager;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.inv.type.RestrictedInventory;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.Scheduler;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.lobby.game.util.ProtectorComponent;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.*;
import static java.util.stream.Collectors.toSet;
import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PreparationActivity extends ComponentActivity implements Skippable, GameStartContext {

    public static final Identifier ARCADE_PARTY_GAME_TAG = ApConstants.identifier("game");
    private static final int GAME_ANNOUNCE_DELAY = Ticks.seconds(3);
    private static final int PREPARATION_TIME = Ticks.seconds(20);
    private static final String GAME_SONG_ID = "ap2_game";
    private final OptionChooser<MiniGame> gameChooser = new OptionChooser<>();
    private final OptionChooser<GameMap> mapChooser = new OptionChooser<>();
    private final ApBaseArgs args;
    private final BaseActivityConfigurator activityConfigurator;
    private int time = 0;
    private boolean skipPreparation = false;
    private boolean gameForced = false;
    private MiniGame miniGame = null;
    private TaskHandle taskHandle = null;
    private BossBarTimer bossBarTimer = null;
    private AnimatedTitle animatedTitle = null;
    private SongWrapper song = null;
    private CompletableFuture<Void> whenTasksDone = null;
    private @Nullable Runnable onScoreUpdate = null;
    private ServerWorld world;
    private GameMap map;
    private DynamicEntityManager dynamicEntityManager;
    private Object3d gameQueueDisplay;
    private @Nullable WeightedSong nextGameSong = null;

    public PreparationActivity(ApBaseArgs args) {
        super(args.miniGameArgs().server(), args.miniGameArgs().logger());

        this.args = args;
        this.activityConfigurator = new BaseActivityConfigurator(this, args);
    }

    @Override
    protected void registerComponents(ComponentBundle componentBundle) {
        componentBundle.add(BuiltinComponents.SCHEDULER)
                .add(BuiltinComponents.BOSS_BAR)
                .add(BuiltinComponents.HOOKS)
                .add(BuiltinComponents.COMMANDS)
                .add(ProtectorComponent.KEY)
                .add(ArcadePartyComponents.SCORE_BOARD);
    }

    @Override
    public void start() {
        super.start();

        activityConfigurator.configureProtector();

        CompletableFuture.supplyAsync(() -> {
            var setupFuture = setupMap(args.miniGameArgs());
            var assetFuture = loadAssets();

            assetFuture.join();

            return setupFuture.join();
        }).whenComplete((res, err) -> {
            if (err != null) {
                args.miniGameArgs().logger().error("Failed to setup preparation activity", err);
            } else {
                onReady(res.world, res.map);
            }
        });
    }

    private CompletableFuture<Void> loadAssets() {
        return args.miniGameArgs().songManager().getSongAndCache(ARCADE_PARTY_GAME_TAG, GAME_SONG_ID)
                .thenAccept(song -> nextGameSong = song.orElse(null));
    }

    static CompletableFuture<SetupResult> setupMap(ApMiniGameArgs miniGameArgs) {
        WorldFacade worldFacade = miniGameArgs.worldFacade();
        Identifier mapId = ApConstants.identifier("preparation");

        return worldFacade.changeMap(mapId, MapOptions.REUSABLE)
                .thenCompose(world -> miniGameArgs.mapFacade().getMap(mapId)
                        .thenApply(map -> new SetupResult(world, map
                                .orElseThrow(() -> new IllegalStateException("Map %s not found".formatted(mapId))))));

    }

    @Override
    public void stop() {
        args.forceGameCommand().setGameEnforcer(args.gameQueue()::setNextGame);

        if (onScoreUpdate != null) {
            args.scoreManager().onChange().unregister(onScoreUpdate);
        }

        removeGameQueue();

        super.stop();
    }

    private void onReady(ServerWorld world, GameMap map) {
        this.world = world;
        this.map = map;

        MapFacade mapFacade = args.miniGameArgs().mapFacade();
        mapFacade.forceMap(null);  // reset forced map

        ScoreManager scoreManager = args.scoreManager();

        if (scoreManager.hasClearWinner()) {
            beginWinSequence();
            return;
        }

        PlayerManager playerManager = args.playerManager();

        if (scoreManager.hasMultipleWinners()) {
            playerManager.enterFinale(scoreManager.getFinalists().collect(toSet()));

            // remove games from the queue that cannot be played in a finale
            args.gameQueue().setFilter(game -> game.canBeFinale(this));
        }

        playerManager.startPreparation();

        scoreManager.incrementRound();

        scoreManager.onChange().register(onScoreUpdate = this::restartActivity);

        activityConfigurator.resetPlayers();
        activityConfigurator.configureHooks();

        world.getWaypointHandler().clear();

        HookRegistrar hooks = component(BuiltinComponents.HOOKS).hooks();
        Scheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();

        dynamicEntityManager = new DynamicEntityManager(world);
        dynamicEntityManager.init(scheduler, hooks);

        if (ApConstants.DEVELOPMENT) {
            giveDevelopmentItems(hooks);
        }

        showLeaderboard();
        displayGameQueue();
        prepareNextMiniGame();

        CommandRegistrar commandRegistrar = component(BuiltinComponents.COMMANDS).commands();

        new SkipCommand(this).register(commandRegistrar);
        new ForceMapCommand(mapFacade, this::getMiniGame).register(commandRegistrar);

        args.forceGameCommand().setGameEnforcer(this::forceGame);
    }

    private void restartActivity() {
        args.scoreManager().decrementRound();

        GameQueue queue = args.gameQueue();

        if (this.miniGame != null) {
            queue.shiftGame(this.miniGame);
        }

        switchActivity(new PreparationActivity(args));
    }

    private void prepareNextMiniGame() {
        if (miniGame == null) {
            miniGame = pickNextGame();
        }

        whenTasksDone = args.miniGameArgs().mapFacade().reloadMaps(miniGame.getId());

        displayGameQueue();
        startTimer().whenDone(this::onTimerEnded);

        taskHandle = component(BuiltinComponents.SCHEDULER).scheduler()
                .interval(this::tick, 1);
    }

    private void showLeaderboard() {
        Translations translations = args.miniGameArgs().translations();
        ScoreManager scoreManager = args.scoreManager();
        ScoreboardComponent component = component(ArcadePartyComponents.SCORE_BOARD);
        CustomScoreboardManager scoreboard = component.scoreboardManager(args.miniGameArgs()::translations);

        var objective = ScoreboardUtil.setupDynamicSidebar(scoreboard, "game.%s.title".formatted(ApConstants.ID));

        // header
        var round = new FixedNumberFormat(Text.literal(String.valueOf(scoreManager.getRound())).formatted(YELLOW));
        objective.createText(translations.translateText("ap2.prepare.round").formatted(GREEN)).setNumberFormat(round);

        if (args.playerManager().isFinale()) {
            addFinalistsToScoreboard(objective);
        } else {
            addPlayerScoresToScoreboard(objective);
        }

        // footer
        if (args.playerManager().isFinale()) {
            objective.createText(player -> {
                String translation = args.playerManager().isParticipating(player)
                        ? "ap2.prepare.win_finale"
                        : "ap2.prepare.spectating";

                return translations.translateText(player, translation).formatted(AQUA);
            }, ScoreboardLayout.BOTTOM);
        } else {
            var requiredScore = styled(scoreManager.getTargetScore()).formatted(YELLOW);
            TranslatedText taskMsg = translations.translateText("ap2.prepare.score_required", requiredScore);
            objective.createText(taskMsg.formatted(AQUA), ScoreboardLayout.BOTTOM);
        }

        objective.createNewline(ScoreboardLayout.BOTTOM);

        // display objective for all players
        for (ServerPlayerEntity player : PlayerLookup.all(args.miniGameArgs().server())) {
            objective.add(player);
        }
    }

    private void addFinalistsToScoreboard(DynamicScoreboardObjective objective) {
        Set<ServerPlayerEntity> finalists = args.scoreManager().getFinalists().collect(toSet());
        Translations translations = args.miniGameArgs().translations();

        objective.createNewline(ScoreboardLayout.TOP);

        objective.createText(translations.translateText("ap2.finale").formatted(YELLOW, BOLD));

        var separator = Text.literal(ApConstants.SCOREBOARD_SEPARATOR_SM).formatted(DARK_GREEN, STRIKETHROUGH);
        objective.createText(separator);

        for (ServerPlayerEntity finalist : finalists) {
            objective.createText(Text.literal("• " + finalist.getNameForScoreboard()).formatted(GREEN));
        }
    }

    private void addPlayerScoresToScoreboard(DynamicScoreboardObjective objective) {
        Translations translations = args.miniGameArgs().translations();
        ScoreManager scoreManager = args.scoreManager();

        if (scoreManager.hasScores()) {
            objective.createNewline(ScoreboardLayout.TOP);

            objective.createText(translations.translateText("ap2.score").formatted(YELLOW, BOLD));

            var separator = Text.literal(ApConstants.SCOREBOARD_SEPARATOR_SM).formatted(DARK_GREEN, STRIKETHROUGH);
            objective.createText(separator);
        }

        // top 5 scores
        int i = 0;

        for (ObjectIntPair<PlayerRef> entry : scoreManager.iterateRankedScores()) {
            if (i++ >= 5) continue;

            PlayerRef ref = entry.left();
            int score = scoreManager.getScore(ref);
            int rank = entry.rightInt();

            objective.setScore(ref.name(), score);

            objective.setDisplayName(ref.name(), Text.literal("#%d ".formatted(rank)).formatted(YELLOW)
                    .append(Text.literal(ref.name()).formatted(GREEN)));
        }
    }

    private void displayGameQueue() {
        JSONObject gameQueue = map.getProperty("game-queue");

        if (gameQueue == null) return;

        Vec3d pos = MapUtil.readVec3d(gameQueue.getJSONArray("pos"));
        double height = max(1, gameQueue.getDouble("height"));
        double yaw = Math.toRadians(gameQueue.optDouble("yaw", 0d) + 90.f);

        var scene = new Scene(new MixedMountContext(world, dynamicEntityManager));

        Translations translations = args.miniGameArgs().translations();

        removeGameQueue();

        gameQueueDisplay = new Object3d(scene);
        gameQueueDisplay.position.set(pos.getX(), pos.getY(), pos.getZ());
        gameQueueDisplay.rotation.setAngleAxis(yaw, new Vector3d(0, 1, 0));

        double offsetY = 0;
        double textHeight = 0.25;

        if (!args.playerManager().isFinale()) {
            offsetY = addUpcomingGames(height, translations, offsetY, textHeight);
        }

        if (miniGame != null) {
            var obj = new TranslatedTextDisplayObject(scene, translations);
            var currentTitle = translations.translateText(miniGame.getTitleKey()).formatted(AQUA);

            obj.controller().configure(controller -> {
                controller.setText(lang -> Text.literal("→ ").formatted(YELLOW)
                                .append(currentTitle.translateTo(lang)));
                controller.setDisplayFlags(DisplayEntity.TextDisplayEntity.DEFAULT_BACKGROUND_FLAG);
            });

            obj.position.set(0, offsetY, 0);

            offsetY += textHeight;

            gameQueueDisplay.addChild(obj);
        }

        var title = new TranslatedTextDisplayObject(scene, translations);

        title.controller().configure(controller -> {
            controller.setText(translations.translateText("ap2.prepare.game_queue").formatted(YELLOW, UNDERLINE, BOLD));
            controller.setDisplayFlags(DisplayEntity.TextDisplayEntity.DEFAULT_BACKGROUND_FLAG);
        });

        title.position.set(0, offsetY, 0);

        gameQueueDisplay.addChild(title);

        scene.add(gameQueueDisplay);
    }

    private void removeGameQueue() {
        if (gameQueueDisplay != null) {
            gameQueueDisplay.detach();
        }
    }

    private double addUpcomingGames(double height, Translations translations, double offsetY, double textHeight) {
        List<GameQueue.Entry> preview = args.gameQueue().preview();

        int reservedSpace = miniGame != null ? 2 : 1;
        int amount = max(0, min(preview.size(), (int) floor(height / textHeight) - reservedSpace));
        preview = preview.subList(0, amount);

        Collections.reverse(preview);

        for (var entry : preview) {
            var obj = new TranslatedTextDisplayObject(gameQueueDisplay.getScene(), translations);

            Formatting color = switch (entry.type()) {
                case REGULAR -> GREEN;
                case VOTED -> GOLD;
                case PRIORITY -> LIGHT_PURPLE;
            };

            boolean mayPossiblyNotBePlayed = !entry.game().canBePlayed(this);

            obj.controller().configure(controller -> {
                TranslatedText text = translations.translateText(entry.game().getTitleKey()).formatted(color);

                if (mayPossiblyNotBePlayed) {
                    controller.setText(lang -> Text.literal("⏳ ").formatted(WHITE)
                            .append(text.translateTo(lang).formatted(ITALIC)));
                } else {
                    controller.setText(text);
                }

                controller.setDisplayFlags(DisplayEntity.TextDisplayEntity.DEFAULT_BACKGROUND_FLAG);
            });

            obj.position.set(0, offsetY, 0);

            offsetY += textHeight;

            gameQueueDisplay.addChild(obj);
        }
        return offsetY;
    }

    private void tick(RunningTask task) {
        int t = time++;

        if (skipPreparation || timedLogic(t)) {
            task.cancel();
            bossBarTimer.stop();
        }
    }

    private boolean timedLogic(int t) {
        if (gameConditionsNoLongerMatch()) {
            announceInvalidGame();
            reset();
            return true;
        }

        if (gameForced) {
            if (t == 0) {
                announceNextGame();
            }
        } else {
            if (t == GAME_ANNOUNCE_DELAY - 40) {
                // "The next game will be %s"
                args.miniGameArgs().translations().translateText("ap2.prepare.next_game").formatted(GRAY)
                        .sendTo(PlayerLookup.all(getServer()));
            } else if (t == GAME_ANNOUNCE_DELAY) {
                announceNextGame();
            }
        }

        return t == PREPARATION_TIME;
    }

    private BossBarTimer startTimer() {
        BossBarComponent bossBars = component(BuiltinComponents.BOSS_BAR);
        Scheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();

        MinecraftServer server = getServer();
        Translations translationService = args.miniGameArgs().translations();

        var label = translationService.translateText("ap2.prepare.next_game_title");

        bossBarTimer = BossBarTimer.builder(translationService, label)
                .withIdentifier(ApConstants.identifier("prepare"))
                .withDurationTicks(PREPARATION_TIME)
                .build();

        bossBarTimer.addPlayers(PlayerLookup.all(server));

        bossBarTimer.start(bossBars, scheduler);

        bossBars.showOnJoin(bossBarTimer.getBossBar());

        return bossBarTimer;
    }

    private void onTimerEnded() {
        if (miniGame == null) {
            prepareNextMiniGame();
            return;
        }

        if (whenTasksDone == null) {
            startGame();
            return;
        }

        whenTasksDone.thenRun(this::startGame);
    }

    private void startGame() {
        Objects.requireNonNull(miniGame, "Mini-Game is not set");
        MiniGameActivity activity = new MiniGameActivity(miniGame, args);

        switchActivity(activity);
    }

    private void beginWinSequence() {
        WinActivity activity = new WinActivity(args);

        switchActivity(activity);
    }

    private void switchActivity(Activity activity) {
        if (animatedTitle != null) {
            animatedTitle.stop();
        }

        if (song != null) {
            song.stop();
        }

        ActivityManager.getInstance().startActivity(activity);
    }

    /**
     * Picks the next game.
     */
    private MiniGame pickNextGame() {
        GameQueue queue = args.gameQueue();

        if (gameForced) {
            return Objects.requireNonNull(queue.pollNextGame(), "Could not determine next game");
        }

        final int maxTries = args.miniGameArgs().miniGames().getGames().size();  // cycle through every registered game once

        for (int tries = 0; tries < maxTries; tries++) {
            MiniGame game = Objects.requireNonNull(queue.pollNextGame(), "Next game from queue is null");

            if (args.playerManager().isFinale() && !game.canBeFinale(this)) continue;

            if (game.canBePlayed(this)) {
                return game;
            }
        }

        throw new IllegalStateException("No game was found that can be played");
    }

    private void forceGame(MiniGame miniGame) {
        Objects.requireNonNull(miniGame, "Tried to force null mini-game");

        if (this.miniGame == miniGame) return;

        GameQueue queue = args.gameQueue();

        if (this.miniGame != null) {
            queue.shiftGame(this.miniGame);
        }

        queue.shiftGame(miniGame);

        reset();

        gameForced = true;
    }

    private void reset() {
        if (taskHandle != null) taskHandle.cancel();
        if (bossBarTimer != null) bossBarTimer.stop();

        this.time = 0;
        this.miniGame = null;
        this.gameForced = false;
    }

    private void announceNextGame() {
        ApMiniGameArgs container = args.miniGameArgs();
        Translations translations = container.translations();
        MinecraftServer server = getServer();
        Logger logger = container.logger();
        TaskScheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();
        DataManager dataManager = container.dataManager();

        WeightedSong nextGameSong = this.nextGameSong;

        if (nextGameSong == null) {
            logger.warn("Sound {} wasn't found, fallback to default sound", GAME_SONG_ID);
        }

        if (animatedTitle != null) {
            animatedTitle.stop();
        }

        animatedTitle = new AnimatedTitle();

        var playedNextMsg = translations.translateText("ap2.prepare.will_be_played_next").formatted(GREEN);
        var separator = Text.literal(ApConstants.SEPARATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);
        String author = dataManager.string(miniGame.getAuthor());

        var players = PlayerLookup.all(server);

        for (ServerPlayerEntity player : players) {
            player.sendMessage(separator);

            var gameTitle = translations.translateText(player, miniGame.getTitleKey()).formatted(AQUA, BOLD);
            player.sendMessage(gameTitle);

            String descriptionKey = miniGame.getDescriptionKey();
            Object[] descArgs = miniGame.getDescriptionArguments();

            var description = translations.translateText(player, descriptionKey, descArgs).formatted(GREEN);

            player.sendMessage(description);

            var createdBy = translations.translateText(player, "ap2.prepare.created_by",
                    styled(author, YELLOW)).formatted(GRAY, ITALIC);

            player.sendMessage(Text.literal(""));
            player.sendMessage(createdBy);

            player.sendMessage(separator);

            animatedTitle.add(new NextGameTitleAnimation(player, gameTitle, playedNextMsg.translateFor(player)));

            if (nextGameSong == null) {
                player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1, 1);
            }
        }

        animatedTitle.start(scheduler, 2);

        if (nextGameSong != null && !players.isEmpty()) {
            this.song = MusicHelper.playSong(nextGameSong, 0.4f, players, server, args.sharedSongCache(), logger);
        }
    }

    /**
     * Check if the game conditions no longer match.
     * @return Whether the game conditions still match.
     */
    private boolean gameConditionsNoLongerMatch() {
        if (miniGame == null) {
            // game wasn't picked yet
            return false;
        }

        return !miniGame.canBePlayed(this);
    }

    /**
     * Announces that the current game is no longer valid and that a new game will be picked.
     */
    private void announceInvalidGame() {
        Translations translations = args.miniGameArgs().translations();

        var gameTitle = translations.translateText(miniGame.getTitleKey()).formatted(YELLOW);
        var msg = translations.translateText("ap2.prepare.game_cannot_be_played", gameTitle).formatted(RED);

        msg.acceptEach(PlayerLookup.all(getServer()), (player, text) -> {
            player.sendMessage(text);
            player.playSoundToPlayer(SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.4f, 0.9f);
        });
    }

    @Override
    public void setSkip(boolean skip) {
        skipPreparation = skip;
    }

    @Override
    public boolean isSkip() {
        return skipPreparation;
    }

    private void giveDevelopmentItems(HookRegistrar hooks) {
        MinecraftServer server = getServer();

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            if (server.getPermissionLevel(player.getGameProfile()) < 2) continue;

            ItemStack gameSelector = new ItemStack(Items.TOTEM_OF_UNDYING);
            gameSelector.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Select Game").styled(style -> style.withItalic(false).withFormatting(YELLOW)));

            ItemStack mapSelector = new ItemStack(Items.HEART_OF_THE_SEA);
            mapSelector.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Select Map").styled(style -> style.withItalic(false).withFormatting(YELLOW)));

            ItemStack skip = new ItemStack(Items.EMERALD_BLOCK);
            skip.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Skip Preparation").styled(style -> style.withItalic(false).withFormatting(GREEN)));

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(0, gameSelector);
            inventory.setStack(1, skip);
            inventory.setStack(8, mapSelector);
        }

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world, hand) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)
                || server.getPermissionLevel(serverPlayer.getGameProfile()) < 2) {

                return ActionResult.PASS;
            }

            ItemStack stack = player.getStackInHand(hand);

            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                openGamePicker(serverPlayer);
            } else if (stack.isOf(Items.EMERALD_BLOCK)) {
                setSkip(true);
                player.sendMessage(Text.literal("Skipped the preparation phase"), false);
            } else if (stack.isOf(Items.HEART_OF_THE_SEA)) {
                openMapPicker(serverPlayer);
            }

            return ActionResult.SUCCESS_SERVER;
        });

        gameChooser.listen(hooks, (game, player) -> {
            forceGame(game);
            player.sendMessage(Text.literal("Forcing mini-game \"%s\"".formatted(game.getId())));
        });

        mapChooser.listen(hooks, (gameMap, player) -> {
            Identifier mapId = gameMap.getDescriptor().getIdentifier();
            args.miniGameArgs().mapFacade().forceMap(mapId);
            player.sendMessage(Text.literal("Next map will be \"%s\"".formatted(mapId)));
        });
    }

    private void openGamePicker(ServerPlayerEntity player) {
        var games = args.miniGameArgs().miniGames().getGames().stream().toList();
        Translations translations = args.miniGameArgs().translations();

        RestrictedInventory inv = gameChooser.createInventory(games, Text.literal("Force Game"),
                game -> IconMaker.createIcon(game, player, translations));

        inv.open(player);
    }

    private void openMapPicker(ServerPlayerEntity player) {
        if (miniGame == null) {
            player.sendMessage(Text.literal("No maps found").formatted(RED));
            return;
        }

        ApMiniGameArgs container = args.miniGameArgs();
        DataManager dataManager = container.dataManager();

        container.mapFacade().getMaps(miniGame.getId()).thenAccept(maps -> {
            Translations translations = container.translations();

            RestrictedInventory inv = mapChooser.createInventory(maps, Text.literal("Force Map"),
                    map -> IconMaker.createIcon(map, player, translations, dataManager));

            inv.open(player);
        });
    }

    @Override
    public Set<ServerPlayerEntity> getParticipants() {
        return args.playerManager().getAsSet();
    }

    private Optional<MiniGame> getMiniGame() {
        return Optional.ofNullable(miniGame);
    }

    public record SetupResult(ServerWorld world, GameMap map) {}
}

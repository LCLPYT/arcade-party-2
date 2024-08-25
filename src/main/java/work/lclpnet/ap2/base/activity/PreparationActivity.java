package work.lclpnet.ap2.base.activity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BossBarComponent;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.game.GameStartContext;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.api.util.music.SongWrapper;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ApContainer;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.base.api.Skippable;
import work.lclpnet.ap2.base.cmd.ForceGameCommand;
import work.lclpnet.ap2.base.cmd.ForceMapCommand;
import work.lclpnet.ap2.base.cmd.SkipCommand;
import work.lclpnet.ap2.base.util.DevChooser;
import work.lclpnet.ap2.base.util.GameDisplay;
import work.lclpnet.ap2.base.util.IconMaker;
import work.lclpnet.ap2.base.util.ScreenConfig;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.util.music.MusicHelper;
import work.lclpnet.ap2.impl.util.title.AnimatedTitle;
import work.lclpnet.ap2.impl.util.title.NextGameTitleAnimation;
import work.lclpnet.ap2.impl.util.world.entity.DynamicEntityManager;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerAdvancementPacketCallback;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.player.PlayerRecipePacketCallback;
import work.lclpnet.kibu.inv.type.RestrictedInventory;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.Scheduler;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.api.GameFinisher;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.lobby.game.util.ProtectorComponent;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PreparationActivity extends ComponentActivity implements Skippable, GameStartContext {

    public static final Identifier ARCADE_PARTY_GAME_TAG = ArcadeParty.identifier("game");
    private static final int GAME_ANNOUNCE_DELAY = Ticks.seconds(3);
    private static final int PREPARATION_TIME = Ticks.seconds(25);
    private static final Identifier GAME_SONG_ID = ArcadeParty.identifier("ap2_game");
    private final DevChooser<MiniGame> gameChooser = new DevChooser<>();
    private final DevChooser<GameMap> mapChooser = new DevChooser<>();
    private final Args args;
    private int time = 0;
    private boolean skipPreparation = false;
    private boolean gameForced = false;
    private MiniGame miniGame = null;
    private TaskHandle taskHandle = null;
    private BossBarTimer bossBarTimer = null;
    private AnimatedTitle animatedTitle = null;
    private SongWrapper song = null;
    private CompletableFuture<Void> whenTasksDone = null;
    private @Nullable GameDisplay gameDisplay = null;

    public PreparationActivity(Args args) {
        super(args.container().server(), args.container().logger());
        this.args = args;
    }

    @Override
    protected void registerComponents(ComponentBundle componentBundle) {
        componentBundle.add(BuiltinComponents.SCHEDULER)
                .add(BuiltinComponents.BOSS_BAR)
                .add(BuiltinComponents.HOOKS)
                .add(BuiltinComponents.COMMANDS)
                .add(ProtectorComponent.KEY);
    }

    @Override
    public void start() {
        super.start();

        component(ProtectorComponent.KEY).configure(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        WorldFacade worldFacade = args.container().worldFacade();

        args.container().mapFacade().getMap(ArcadeParty.identifier("preparation")).ifPresentOrElse(map -> {
            Identifier mapId = map.getDescriptor().getIdentifier();

            // change to preparation map, loading it if not yet done
            worldFacade.changeMap(mapId, MapOptions.REUSABLE)
                    .thenAccept(world -> onReady(map, world))
                    .exceptionally(throwable -> {
                        getLogger().error("Failed to change map", throwable);
                        args.finisher().finishGame();
                        return null;
                    });
        }, () -> {
            getLogger().error("Could not find preparation map");
            args.finisher().finishGame();
        });
    }

    @Override
    public void stop() {
        args.forceGameCommand.setGameEnforcer(args.gameQueue::setNextGame);
        super.stop();
    }

    private void onReady(GameMap map, ServerWorld world) {
        PlayerManager playerManager = args.playerManager();
        playerManager.startPreparation();

        PlayerUtil playerUtil = args.container().playerUtil();
        playerUtil.resetToDefaults();
        playerManager.forEach(playerUtil::resetPlayer);

        HookRegistrar hooks = component(BuiltinComponents.HOOKS).hooks();

        hooks.registerHook(PlayerConnectionHooks.JOIN, this::onJoin);
        hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, event -> !event.player().isCreativeLevelTwoOp());
        hooks.registerHook(PlayerAdvancementPacketCallback.HOOK, (player, packet) -> true);
        hooks.registerHook(PlayerRecipePacketCallback.HOOK, (player, packet) -> true);

        if (ApConstants.DEVELOPMENT) {
            giveDevelopmentItems(hooks);
        }

        Scheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();

        setupDisplays(map, world, hooks, scheduler);
        showLeaderboard();
        displayGameQueue();
        prepareNextMiniGame(scheduler);

        CommandRegistrar commandRegistrar = component(BuiltinComponents.COMMANDS).commands();

        new SkipCommand(this).register(commandRegistrar);
        new ForceMapCommand(args.container.mapFacade(), this::getMiniGame).register(commandRegistrar);

        args.forceGameCommand.setGameEnforcer(this::forceGame);
    }

    private void prepareNextMiniGame(Scheduler scheduler) {
        if (miniGame == null) {
            miniGame = pickNextGame();
        }

        whenTasksDone = args.container.mapFacade().reloadMaps(miniGame.getId());

        displayGameQueue();
        startTimer().whenDone(this::onTimerEnded);

        taskHandle = scheduler
                .interval(this::tick, 1);
    }

    private void onJoin(ServerPlayerEntity player) {
        PlayerManager playerManager = args.playerManager();

        boolean spectator = playerManager.isPermanentSpectator(player) || !playerManager.offer(player);

        PlayerUtil.State state = spectator ? PlayerUtil.State.SPECTATOR : PlayerUtil.State.DEFAULT;
        args.container().playerUtil().resetPlayer(player, state);
    }

    private void setupDisplays(GameMap map, ServerWorld world, HookRegistrar hooks, Scheduler scheduler) {
        DynamicEntityManager manager = new DynamicEntityManager(world);
        manager.init(scheduler);

        Translations translations = args.container().translations();

        JSONObject gameDisplayJson = map.getProperty("game-display");

        if (gameDisplayJson != null) {
            var config = ScreenConfig.fromJson(gameDisplayJson);

            var gameDisplay = new GameDisplay(config, world, hooks, translations, manager);
            gameDisplay.displayDefault();
        }
    }

    private void showLeaderboard() {
        // TODO implement
    }

    private void displayGameQueue() {
        // TODO implement
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
                args.container().translations().translateText("ap2.prepare.next_game").formatted(GRAY)
                        .sendTo(PlayerLookup.all(getServer()));
            } else if (t == GAME_ANNOUNCE_DELAY) {
                announceNextGame();
            }
        }

        return t == PREPARATION_TIME || allPlayersAreReady();
    }

    private BossBarTimer startTimer() {
        BossBarComponent bossBars = component(BuiltinComponents.BOSS_BAR);
        Scheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();

        MinecraftServer server = getServer();
        Translations translationService = args.container().translations();

        var label = translationService.translateText("ap2.prepare.next_game_title");

        bossBarTimer = BossBarTimer.builder(translationService, label)
                .withIdentifier(ArcadeParty.identifier("prepare"))
                .withDurationTicks(PREPARATION_TIME)
                .build();

        bossBarTimer.addPlayers(PlayerLookup.all(server));

        bossBarTimer.start(bossBars, scheduler);

        bossBars.showOnJoin(bossBarTimer.getBossBar());

        return bossBarTimer;
    }

    private boolean allPlayersAreReady() {
        // TODO implement
        return false;
    }

    private void onTimerEnded() {
        if (miniGame == null) {
            prepareNextMiniGame(component(BuiltinComponents.SCHEDULER).scheduler());
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

        if (animatedTitle != null) {
            animatedTitle.stop();
        }

        if (song != null) {
            song.stop();
        }

        MiniGameActivity activity = new MiniGameActivity(miniGame, args);
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

        final int maxTries = args.container().miniGames().getGames().size();  // cycle through every registered game once

        for (int tries = 0; tries < maxTries; tries++) {
            MiniGame game = Objects.requireNonNull(queue.pollNextGame(), "Next game from queue is null");

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
        ApContainer container = args.container();
        Translations translations = container.translations();
        MinecraftServer server = getServer();
        SongManager songManager = container.songManager();
        Logger logger = container.logger();
        TaskScheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();
        DataManager dataManager = container.dataManager();

        var song = songManager.getSong(PreparationActivity.ARCADE_PARTY_GAME_TAG, GAME_SONG_ID);
        boolean soundFallback = song.isEmpty();

        if (soundFallback) {
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

            if (soundFallback) {
                player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1, 1);
            }
        }

        animatedTitle.start(scheduler, 2);

        if (!soundFallback) {
            this.song = MusicHelper.playSong(song.get(), 0.4f, players, server, args.sharedSongCache(), logger);
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
        Translations translations = args.container().translations();

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

                return TypedActionResult.pass(ItemStack.EMPTY);
            }

            ItemStack stack = player.getStackInHand(hand);

            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                openGamePicker(serverPlayer);
            } else if (stack.isOf(Items.EMERALD_BLOCK)) {
                setSkip(true);
                player.sendMessage(Text.literal("Skipped the preparation phase"));
            } else if (stack.isOf(Items.HEART_OF_THE_SEA)) {
                openMapPicker(serverPlayer);
            }

            return TypedActionResult.success(stack);
        });

        gameChooser.listen(hooks, (game, player) -> {
            forceGame(game);
            player.sendMessage(Text.literal("Forcing mini-game \"%s\"".formatted(game.getId())));
        });

        mapChooser.listen(hooks, (gameMap, player) -> {
            Identifier mapId = gameMap.getDescriptor().getIdentifier();
            args.container().mapFacade().forceMap(mapId);
            player.sendMessage(Text.literal("Next map will be \"%s\"".formatted(mapId)));
        });
    }

    private void openGamePicker(ServerPlayerEntity player) {
        var games = args.container().miniGames().getGames().stream().toList();
        Translations translations = args.container().translations();

        RestrictedInventory inv = gameChooser.createInventory(games, Text.literal("Force Game"),
                game -> IconMaker.createIcon(game, player, translations));

        inv.open(player);
    }

    private void openMapPicker(ServerPlayerEntity player) {
        if (miniGame == null) {
            player.sendMessage(Text.literal("No maps found").formatted(RED));
            return;
        }

        ApContainer container = args.container();
        DataManager dataManager = container.dataManager();

        var maps = container.mapFacade().getMaps(miniGame.getId());
        Translations translations = container.translations();

        RestrictedInventory inv = mapChooser.createInventory(maps, Text.literal("Force Map"),
                map -> IconMaker.createIcon(map, player, translations, dataManager));

        inv.open(player);
    }

    @Override
    public Set<ServerPlayerEntity> getParticipants() {
        return args.playerManager().getAsSet();
    }

    private Optional<MiniGame> getMiniGame() {
        return Optional.ofNullable(miniGame);
    }

    public record Args(ApContainer container, GameQueue gameQueue, PlayerManager playerManager,
                       ForceGameCommand forceGameCommand, SongCache sharedSongCache, GameFinisher finisher) {}
}

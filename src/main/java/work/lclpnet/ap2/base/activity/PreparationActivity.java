package work.lclpnet.ap2.base.activity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BossBarComponent;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.api.game.GameStartContext;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ApContainer;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.base.api.Skippable;
import work.lclpnet.ap2.base.cmd.ForceGameCommand;
import work.lclpnet.ap2.base.cmd.SkipCommand;
import work.lclpnet.ap2.base.util.DevGameChooser;
import work.lclpnet.ap2.base.util.MapIconMaker;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.util.music.MusicHelper;
import work.lclpnet.ap2.impl.util.title.AnimatedTitle;
import work.lclpnet.ap2.impl.util.title.NextGameTitleAnimation;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerAdvancementPacketCallback;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.player.PlayerRecipePacketCallback;
import work.lclpnet.kibu.inv.type.RestrictedInventory;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.ext.PluginContext;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.Scheduler;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.lobby.game.util.ProtectorComponent;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.Objects;
import java.util.Set;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PreparationActivity extends ComponentActivity implements Skippable, GameStartContext {

    public static final Identifier ARCADE_PARTY_GAME_TAG = ArcadeParty.identifier("game");
    private static final int GAME_ANNOUNCE_DELAY = Ticks.seconds(3);
    private static final int PREPARATION_TIME = Ticks.seconds(25);
    private static final Identifier GAME_SONG_ID = ArcadeParty.identifier("ap2_game");
    private final DevGameChooser gameChooser = new DevGameChooser();
    private final Args args;
    private int time = 0;
    private boolean skipPreparation = false;
    private boolean gameForced = false;
    private MiniGame miniGame = null;
    private TaskHandle taskHandle = null;
    private BossBarTimer bossBarTimer = null;
    private AnimatedTitle animatedTitle = null;

    public PreparationActivity(Args args) {
        super(args.pluginContext());
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

        worldFacade.changeMap(ArcadeParty.identifier("preparation"), MapOptions.REUSABLE)
                .thenRun(this::onReady)
                .exceptionally(throwable -> {
                    getLogger().error("Failed to change map", throwable);
                    return null;
                });
    }

    @Override
    public void stop() {
        args.forceGameCommand.setGameEnforcer(args.gameQueue::setNextGame);
        super.stop();
    }

    private void onReady() {
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

        showLeaderboard();
        displayGameQueue();
        prepareNextMiniGame();

        CommandRegistrar commandRegistrar = component(BuiltinComponents.COMMANDS).commands();

        new SkipCommand(this).register(commandRegistrar);

        args.forceGameCommand.setGameEnforcer(this::forceGame);
    }

    private void prepareNextMiniGame() {
        if (miniGame == null) {
            miniGame = pickNextGame();
        }

        displayGameQueue();
        startTimer().whenDone(this::onTimerEnded);

        taskHandle = component(BuiltinComponents.SCHEDULER).scheduler()
                .interval(this::tick, 1);
    }

    private void onJoin(ServerPlayerEntity player) {
        PlayerManager playerManager = args.playerManager();

        boolean spectator = playerManager.isPermanentSpectator(player) || !playerManager.offer(player);

        PlayerUtil.State state = spectator ? PlayerUtil.State.SPECTATOR : PlayerUtil.State.DEFAULT;
        args.container().playerUtil().resetPlayer(player, state);
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
                args.container().translationService().translateText("ap2.prepare.next_game").formatted(GRAY)
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
        TranslationService translationService = args.container().translationService();

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
            prepareNextMiniGame();
            return;
        }

        startGame();
    }

    private void startGame() {
        Objects.requireNonNull(miniGame, "Mini-Game is not set");

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
        TranslationService translations = args.container().translationService();
        MinecraftServer server = getServer();
        SongManager songManager = args.container.songManager();
        Logger logger = args.container().logger();
        TaskScheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();

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
        var separator = Text.literal(ApConstants.SEPERATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);

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
                    styled(miniGame.getAuthor(), YELLOW)).formatted(GRAY, ITALIC);

            player.sendMessage(Text.literal(""));
            player.sendMessage(createdBy);

            player.sendMessage(separator);

            animatedTitle.add(new NextGameTitleAnimation(player, gameTitle, playedNextMsg.translateFor(player)));

            if (soundFallback) {
                player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1, 1);
            }
        }

        animatedTitle.start(scheduler, 2);

        if (!soundFallback) {
            MusicHelper.playSong(song.get(), 0.4f, players, server, args.sharedSongCache(), logger);
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
        TranslationService translations = args.container().translationService();

        var gameTitle = translations.translateText(miniGame.getTitleKey()).formatted(YELLOW);
        var msg = translations.translateText("ap2.prepare.game_cannot_be_played", gameTitle).formatted(RED);

        msg.acceptEach(PlayerLookup.all(getServer()), (player, text) -> {
            player.sendMessage(text);
            player.playSound(SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.4f, 0.9f);
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

            ItemStack selector = new ItemStack(Items.TOTEM_OF_UNDYING);
            selector.setCustomName(Text.literal("Select Game").styled(style -> style.withItalic(false).withFormatting(YELLOW)));

            ItemStack skip = new ItemStack(Items.EMERALD_BLOCK);
            skip.setCustomName(Text.literal("Skip Preparation").styled(style -> style.withItalic(false).withFormatting(GREEN)));

            PlayerInventory inventory = player.getInventory();
            inventory.setStack(0, selector);
            inventory.setStack(1, skip);
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
            }

            return TypedActionResult.success(stack);
        });

        hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, event -> {
            this.onModifyInventory(event);
            return false;
        });
    }

    private void onModifyInventory(PlayerInventoryHooks.ClickEvent event) {
        if (event.action() != SlotActionType.PICKUP) return;

        ServerPlayerEntity player = event.player();
        MinecraftServer server = getServer();

        if (server.getPermissionLevel(player.getGameProfile()) < 2) return;

        Inventory inventory = event.inventory();
        if (inventory == null) return;

        Slot slot = event.handlerSlot();
        if (slot == null) return;

        int slotIndex = slot.getIndex();

        MiniGame game = gameChooser.getGame(inventory, slotIndex);

        if (game == null) return;

        forceGame(game);

        player.closeHandledScreen();
        player.sendMessage(Text.literal("Forcing mini-game \"%s\"".formatted(game.getId())));
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.5f, 2f);
    }

    private void openGamePicker(ServerPlayerEntity player) {
        var games = args.container().miniGames().getGames().stream().toList();
        TranslationService translations = args.container().translationService();

        int rows = Math.max(1, Math.min(6, (int) Math.ceil(games.size() / 9d)));

        RestrictedInventory inv = new RestrictedInventory(rows, Text.literal("Force Game"));

        int capacity = rows * 9;
        int i = 0;

        for (MiniGame game : games) {
            if (i >= capacity) {
                getLogger().warn("Not enough space in inventory");
                break;
            }

            ItemStack icon = MapIconMaker.createIcon(game, player, translations);

            inv.setStack(i++, icon);
        }

        gameChooser.registerInventory(inv, games);

        inv.open(player);
    }

    @Override
    public Set<ServerPlayerEntity> getParticipants() {
        return args.playerManager().getAsSet();
    }

    public record Args(PluginContext pluginContext, ApContainer container, GameQueue gameQueue,
                       PlayerManager playerManager, ForceGameCommand forceGameCommand, SongCache sharedSongCache) {}
}

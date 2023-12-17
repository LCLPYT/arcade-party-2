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
import net.minecraft.util.TypedActionResult;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BossBarComponent;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.PlayerManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.ApContainer;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.base.api.Skippable;
import work.lclpnet.ap2.base.cmd.ForceGameCommand;
import work.lclpnet.ap2.base.cmd.SkipCommand;
import work.lclpnet.ap2.base.util.DevGameChooser;
import work.lclpnet.ap2.base.util.MapIconMaker;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerAdvancementPacketCallback;
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.inv.type.RestrictedInventory;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.ext.PluginContext;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.Scheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.lobby.game.util.ProtectorComponent;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.Objects;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PreparationActivity extends ComponentActivity implements Skippable {

    private static final int GAME_ANNOUNCE_DELAY = Ticks.seconds(3);
    private static final int PREPARATION_TIME = Ticks.seconds(25);
    private final DevGameChooser gameChooser = new DevGameChooser();
    private final Args args;
    private int time = 0;
    private boolean skipPreparation = false;
    private MiniGame miniGame = null;

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

        if (ApConstants.DEVELOPMENT) {
            giveDevelopmentItems(hooks);
        }

        showLeaderboard();
        displayGameQueue();
        pickNextGame();
        displayGameQueue();
        startTimer();

        component(BuiltinComponents.SCHEDULER).scheduler()
                .interval(this::tick, 1)
                .whenComplete(this::startGame);

        CommandRegistrar commandRegistrar = component(BuiltinComponents.COMMANDS).commands();

        new SkipCommand(this).register(commandRegistrar);
        args.forceGameCommand.setGameEnforcer(this::forceGame);
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
        }
    }

    private boolean timedLogic(int t) {
        if (gameConditionsNoLongerMatch()) {
            time = 0;
            announceInvalidGame();
            miniGame = null;

            return false;
        }

        if (t == GAME_ANNOUNCE_DELAY - 40) {
            // "The next game will be %s"
            args.container().translationService().translateText("ap2.prepare.next_game").formatted(GRAY)
                            .sendTo(PlayerLookup.all(getServer()));
        } else if (t == GAME_ANNOUNCE_DELAY) {
            announceNextGame();
        }

        return t == PREPARATION_TIME || allPlayersAreReady();
    }

    private void startTimer() {
        BossBarComponent bossBars = component(BuiltinComponents.BOSS_BAR);
        Scheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();

        MinecraftServer server = getServer();
        TranslationService translationService = args.container().translationService();

        var label = translationService.translateText("ap2.prepare.next_game_title");

        BossBarTimer timer = BossBarTimer.builder(translationService, label)
                .withIdentifier(ArcadeParty.identifier("prepare"))
                .withDurationTicks(PREPARATION_TIME)
                .build();

        timer.addPlayers(PlayerLookup.all(server));

        timer.start(bossBars, scheduler);
        bossBars.showOnJoin(timer.getBossBar());
    }

    private boolean allPlayersAreReady() {
        // TODO implement
        return false;
    }

    private void startGame() {
        MiniGameActivity activity = new MiniGameActivity(miniGame, args);
        ActivityManager.getInstance().startActivity(activity);
    }

    /**
     * Picks the next game.
     */
    private void pickNextGame() {
        miniGame = Objects.requireNonNull(args.gameQueue().pollNextGame(), "Could not determine next game");
    }

    private void forceGame(MiniGame miniGame) {
        Objects.requireNonNull(miniGame);

        if (this.miniGame != null) {
            args.gameQueue().shiftGame(this.miniGame);
        }

        this.miniGame = miniGame;
    }

    private void announceNextGame() {
        TranslationService translationService = args.container().translationService();

        var separator = Text.literal(ApConstants.SEPERATOR)
                .formatted(DARK_GREEN, STRIKETHROUGH, BOLD);

        for (ServerPlayerEntity player : PlayerLookup.all(getServer())) {

            player.sendMessage(separator);

            var gameTitle = translationService.translateText(player, miniGame.getTitleKey()).formatted(AQUA, BOLD);
            player.sendMessage(gameTitle);

            String descriptionKey = miniGame.getDescriptionKey();
            Object[] descArgs = miniGame.getDescriptionArguments();

            var description = translationService.translateText(player, descriptionKey, descArgs).formatted(GREEN);

            player.sendMessage(description);

            var createdBy = translationService.translateText(player, "ap2.prepare.created_by",
                            styled(miniGame.getAuthor(), YELLOW)).formatted(GRAY, ITALIC);

            player.sendMessage(Text.literal(""));
            player.sendMessage(createdBy);

            player.sendMessage(separator);

            // TODO animate
            Title.get(player).title(gameTitle);

            // TODO replace with game jingle
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1, 1);
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

        return !miniGame.canBePlayed();
    }

    /**
     * Announces that the current game is no longer valid and that a new game will be picked.
     */
    private void announceInvalidGame() {
        // TODO implement
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

    public record Args(PluginContext pluginContext, ApContainer container, GameQueue gameQueue,
                       PlayerManager playerManager, ForceGameCommand forceGameCommand) {}
}

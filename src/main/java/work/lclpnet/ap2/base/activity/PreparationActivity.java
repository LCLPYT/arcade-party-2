package work.lclpnet.ap2.base.activity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BossBarComponent;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.base.api.Skippable;
import work.lclpnet.ap2.base.cmd.SkipCommand;
import work.lclpnet.ap2.impl.BossBarTimer;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.ext.PluginContext;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.Scheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.api.WorldFacade;

import java.util.Objects;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PreparationActivity extends ComponentActivity implements Skippable {

    private static final int INITIAL_DELAY = 60;
    private static final int PREPARATION_TIME = 500;
    private final WorldFacade worldFacade;
    private final TranslationService translationService;
    private final GameQueue gameQueue;
    private int time = 0;
    private boolean skipPreparation = false;
    private MiniGame miniGame = null;

    public PreparationActivity(PluginContext pluginContext, WorldFacade worldFacade,
                               TranslationService translationService, GameQueue gameQueue) {
        super(pluginContext);
        this.worldFacade = worldFacade;
        this.translationService = translationService;
        this.gameQueue = gameQueue;
    }

    @Override
    protected void registerComponents(ComponentBundle componentBundle) {
        componentBundle.add(BuiltinComponents.SCHEDULER)
                .add(BuiltinComponents.BOSS_BAR)
                .add(BuiltinComponents.HOOKS)
                .add(BuiltinComponents.COMMANDS);
    }

    @Override
    public void start() {
        super.start();

        worldFacade.changeMap(ArcadeParty.identifier("preparation"), MapOptions.REUSABLE)
                .thenRun(this::onReady)
                .exceptionally(throwable -> {
                    getLogger().error("Failed to change map", throwable);
                    return null;
                });
    }

    private void onReady() {
        showLeaderboard();
        displayGameQueue();

        component(BuiltinComponents.SCHEDULER).scheduler()
                .interval(this::tick, 1)
                .whenComplete(this::startGame);

        CommandRegistrar commandRegistrar = component(BuiltinComponents.COMMANDS).commands();

        new SkipCommand(this).register(commandRegistrar);
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

        updateTimer();
    }

    private boolean timedLogic(int t) {
        if (t < INITIAL_DELAY) return false;

        if (t == INITIAL_DELAY) {
            startTimer();
            pickNextGame();
            displayGameQueue();  // update game queue as it could have changed
            announceNextGame();
            return false;
        }

        if (gameConditionsNoLongerMatch()) {
            time = 0;
            announceInvalidGame();
            miniGame = null;

            return false;
        }

        return t == PREPARATION_TIME || allPlayersAreReady();

    }

    private void startTimer() {
        BossBarComponent bossBars = component(BuiltinComponents.BOSS_BAR);
        Scheduler scheduler = component(BuiltinComponents.SCHEDULER).scheduler();

        MinecraftServer server = getServer();

        var label = translationService.translateText("ap2.prepare.next_game_title");

        BossBarTimer timer = BossBarTimer.builder(translationService, label)
                .withIdentifier(ArcadeParty.identifier("prepare"))
                .withDurationTicks(Ticks.seconds(25))
                .build();

        timer.addPlayers(PlayerLookup.all(server));

        timer.whenDone(() -> System.out.println("Game start"));

        timer.start(bossBars, scheduler);
    }

    private boolean allPlayersAreReady() {
        // TODO implement
        return false;
    }

    private void updateTimer() {

    }

    private void startGame() {
        // TODO change to game activity
    }

    /**
     * Picks the next game.
     */
    private void pickNextGame() {
        miniGame = Objects.requireNonNull(gameQueue.pollNextGame(), "Could not determine next game");
    }

    private void announceNextGame() {
        for (ServerPlayerEntity player : PlayerLookup.all(getServer())) {
            var gameTitle = translationService.translateText(player, miniGame.getTitleKey()).formatted(AQUA, BOLD);

            // "The next game will be %s"
            player.sendMessage(translationService.translateText(player, "ap2.prepare.next_game", gameTitle)
                    .formatted(GRAY));

            var separator = Text.literal("===================================")
                    .formatted(DARK_GREEN, STRIKETHROUGH, BOLD);

            player.sendMessage(separator);

            var description = translationService.translateText(player, miniGame.getDescriptionKey()).formatted(GREEN);

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

    private void displayGameName() {

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
}

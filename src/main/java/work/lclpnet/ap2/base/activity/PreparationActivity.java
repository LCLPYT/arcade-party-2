package work.lclpnet.ap2.base.activity;

import work.lclpnet.activity.ComponentActivity;
import work.lclpnet.activity.component.ComponentBundle;
import work.lclpnet.activity.component.builtin.BuiltinComponents;
import work.lclpnet.ap2.api.Skippable;
import work.lclpnet.ap2.api.WorldFacade;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.base.cmd.SkipCommand;
import work.lclpnet.kibu.plugin.cmd.CommandRegistrar;
import work.lclpnet.kibu.plugin.ext.PluginContext;
import work.lclpnet.kibu.scheduler.api.RunningTask;

public class PreparationActivity extends ComponentActivity implements Skippable {

    private static final int INITIAL_DELAY = 60;
    private static final int PREPARATION_TIME = 500;
    private final WorldFacade worldFacade;
    private int time = 0;
    private boolean skipPreparation = false;

    public PreparationActivity(PluginContext pluginContext, WorldFacade worldFacade) {
        super(pluginContext);
        this.worldFacade = worldFacade;
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

        worldFacade.changeMap(ArcadeParty.identifier("preparation"))
                .thenRun(this::onReady)
                .exceptionally(throwable -> {
                    getLogger().error("Failed to change map", throwable);
                    return null;
                });
    }

    @Override
    public void stop() {
        super.stop();

        worldFacade.deleteMap();
    }

    private void onReady() {
        showLeaderboard();
        displayGameQueue();

        component(BuiltinComponents.SCHEDULER).scheduler()
                .interval(this::tick, 1)
                .whenComplete(this::startGame);

        component(BuiltinComponents.SCHEDULER).scheduler().timeout(() -> {
            worldFacade.changeMap(ArcadeParty.identifier("preparation"))
                    .thenRun(() -> System.out.println("Changed map"))
                    .exceptionally(throwable -> {
                        getLogger().error("Failed to change map", throwable);
                        return null;
                    });
        }, 100);

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
            pickNextGame();
            displayGameQueue();  // update game queue as it could have changed
            announceNextGame();
            return false;
        }

        if (gameConditionsNoLongerMatch()) {
            time = 0;
            announceInvalidGame();
            return false;
        }

        return t == PREPARATION_TIME || allPlayersAreReady();

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
     * If the next game cannot be played, skips the queue until a playable game is found.
     * When the queue is exhausted, it is complemented by a secondary queue.
     */
    private void pickNextGame() {
        // TODO implement
    }

    private void announceNextGame() {
        // TODO implement
//        playStartingJingle();
//        showTitle();
//        exlainGame();
    }

    /**
     * Check if the game conditions no longer match.
     * @return Whether the game conditions still match.
     */
    private boolean gameConditionsNoLongerMatch() {
        // TODO implement
        return false;
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

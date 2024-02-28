package work.lclpnet.ap2.impl.game;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.GameOverListener;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.GameWinnersFactory;
import work.lclpnet.ap2.api.game.data.PlayerSubjectRefFactory;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.Set;
import java.util.function.Supplier;

public class WinManager<T, Ref extends SubjectRef> {

    private final MiniGameHandle gameHandle;
    private final Supplier<DataContainer<T, Ref>> dataSupplier;
    private final PlayerSubjectRefFactory<Ref> refs;
    private final GameWinnersFactory<T, Ref> winnersFactory;
    private final Hook<GameOverListener> gameOverHook = HookFactory.createArrayBacked(GameOverListener.class, hooks -> () -> {
        for (GameOverListener hook : hooks) {
            hook.onGameOver();
        }
    });
    private boolean gameOver = false;

    public WinManager(MiniGameHandle gameHandle, Supplier<DataContainer<T, Ref>> dataSupplier,
                      PlayerSubjectRefFactory<Ref> refs, GameWinnersFactory<T, Ref> winnersFactory) {
        this.gameHandle = gameHandle;
        this.dataSupplier = dataSupplier;
        this.refs = refs;
        this.winnersFactory = winnersFactory;
    }

    public void win(@Nullable T winner) {
        if (winner == null) {
            winNobody();
            return;
        }

        win(Set.of(winner));
    }

    public void winNobody() {
        win(Set.of());
    }

    public synchronized void win(Set<T> winners) {
        if (this.gameOver) return;

        gameOver = true;
        gameOverHook.invoker().onGameOver();

        gameHandle.resetGameScheduler();

        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        var data = dataSupplier.get();

        winners.forEach(data::ensureTracked);
        data.freeze();

        var winSequence = new WinSequence<>(gameHandle, data, refs);
        var gameWinners = winnersFactory.create(winners);

        winSequence.start(gameWinners);
    }

    public void addListener(GameOverListener listener) {
        gameOverHook.register(listener);
    }

    public boolean isGameOver() {
        return gameOver;
    }
}

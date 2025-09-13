package work.lclpnet.ap2.impl.game;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.GameOverListener;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.game.data.*;
import work.lclpnet.ap2.api.stats.StatsManager;
import work.lclpnet.ap2.api.stats.StatsResult;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.SupremeDataContainer;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class WinManager<T, Ref extends SubjectRef> {

    private final MiniGameHandle gameHandle;
    private final Supplier<DataContainer<T, Ref>> dataSupplier;
    private final PlayerSubjectRefFactory<Ref> playerRefs;
    private final Function<ServerPlayerEntity, Optional<T>> subjectMapper;
    private final Function<DataContainer<T, Ref>, GenericGameResult<Ref>> winnersFactory;
    private final Hook<GameOverListener> gameOverHook = HookFactory.createArrayBacked(GameOverListener.class, hooks -> () -> {
        for (GameOverListener hook : hooks) {
            hook.onGameOver();
        }
    });
    private final SupremeDataContainer<T, Ref> forcedWinners;
    @Getter
    private volatile boolean gameOver = false;
    @Setter
    private @Nullable StatsManager<Ref> statsManager = null;

    public WinManager(MiniGameHandle gameHandle,
                      Supplier<DataContainer<T, Ref>> dataSupplier,
                      Function<ServerPlayerEntity, Optional<T>> subjectMapper,
                      SubjectRefFactory<T, Ref> subjectRefs,
                      PlayerSubjectRefFactory<Ref> playerRefs,
                      Function<DataContainer<T, Ref>, GenericGameResult<Ref>> winnersFactory) {

        this.gameHandle = gameHandle;
        this.dataSupplier = dataSupplier;
        this.subjectMapper = subjectMapper;
        this.playerRefs = playerRefs;
        this.winnersFactory = winnersFactory;
        this.forcedWinners = new SupremeDataContainer<>(subjectRefs);
    }

    public Action<Runnable> complete() {
        return startWinSequence(MiniGameResults.Status.SUCCESS);
    }

    public Action<Runnable> cancel() {
        return startWinSequence(MiniGameResults.Status.CANCELLED);
    }

    private synchronized Action<Runnable> startWinSequence(MiniGameResults.Status status) {
        if (this.gameOver) return Action.noop();

        gameOver = true;
        gameOverHook.invoker().onGameOver();

        gameHandle.resetGameScheduler();

        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        // concat forced winners, then the rest
        var finalData = new CombinedDataContainer<>(List.of(forcedWinners, dataSupplier.get().copy()));
        GenericGameResult<Ref> result = winnersFactory.apply(finalData);
        var statsId = submitStats(result);
        var winSequence = new WinSequence<>(gameHandle, finalData, playerRefs, result, status, statsId);

        return winSequence.start();
    }

    private @NotNull CompletableFuture<Optional<UUID>> submitStats(GenericGameResult<Ref> result) {
        var statsManager = this.statsManager;

        if (statsManager == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        statsManager.freeze();

        StatsResult<Ref> stats = statsManager.getResult(result);

        return gameHandle.submitStats(stats)
                .thenApply(Optional::of)
                .exceptionally(err -> {
                    gameHandle.getLogger().error("Failed to submit stats", err);
                    return Optional.empty();
                });
    }

    public void addListener(GameOverListener listener) {
        gameOverHook.register(listener);
    }

    public void checkForLastRemaining() {
        Set<T> participatingSubjects = gameHandle.getParticipants().stream()
                .map(subjectMapper)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        int size = participatingSubjects.size();

        if (size > 1) return;

        if (size == 1) {
            T lastRemaining = participatingSubjects.iterator().next();

            dataSupplier.get().add(lastRemaining);
        }

        complete();
    }

    public void forceWin(@Nullable Set<T> winners) {
        forcedWinners.clear();

        if (winners != null) {
            winners.forEach(forcedWinners::add);
        }

        complete();
    }
}

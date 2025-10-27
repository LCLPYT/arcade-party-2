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
import work.lclpnet.lobby.game.map.GameMap;
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
    private final Supplier<GameMap> map;
    private final Data<T, Ref> data;
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

    public WinManager(MiniGameHandle gameHandle, Supplier<GameMap> map, Data<T, Ref> data) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.data = data;
        this.forcedWinners = new SupremeDataContainer<>(data.subjectRefs());
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
        var finalData = new CombinedDataContainer<>(List.of(forcedWinners, data.supplier().get().copy()));
        GenericGameResult<Ref> result = data.winnersFactory().apply(finalData);
        var statsId = submitStats(result);
        var winSequence = new WinSequence<>(gameHandle, finalData, data.playerRefs(), result, status, statsId);

        return winSequence.start();
    }

    private @NotNull CompletableFuture<Optional<UUID>> submitStats(GenericGameResult<Ref> result) {
        var statsManager = this.statsManager;

        if (statsManager == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        statsManager.freeze();

        StatsResult stats = statsManager.getResult(gameHandle.getGameInfo(), map.get(), result);

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
                .map(data.subjectMapper())
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        int size = participatingSubjects.size();

        if (size > 1) return;

        if (size == 1) {
            T lastRemaining = participatingSubjects.iterator().next();

            data.supplier().get().add(lastRemaining);
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

    public record Data<T, Ref extends SubjectRef>(
            Supplier<DataContainer<T, Ref>> supplier,
            Function<ServerPlayerEntity, Optional<T>> subjectMapper,
            SubjectRefFactory<T, Ref> subjectRefs,
            PlayerSubjectRefFactory<Ref> playerRefs,
            Function<DataContainer<T, Ref>, GenericGameResult<Ref>> winnersFactory
    ) {}
}

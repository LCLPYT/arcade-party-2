package work.lclpnet.ap2.impl.game;

import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.Set;
import java.util.function.Consumer;

public abstract class DefaultGameInstance extends BaseGameInstance implements ParticipantListener {

    private boolean gameOver = false;

    public DefaultGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public ParticipantListener getParticipantListener() {
        return this;
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        // in this game, this will only be called when a participant quits
        var participants = gameHandle.getParticipants().getAsSet();

        if (participants.size() > 1) return;

        var winner = participants.stream().findAny();

        DataContainer data = getData();

        if (winner.isEmpty()) {
            winner = data.getBestPlayer(gameHandle.getServer());
        }

        win(winner.orElse(null));
    }

    public void win(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            winNobody();
            return;
        }

        win(Set.of(player));
    }

    public void winNobody() {
        win(Set.of());
    }

    public synchronized void win(Set<ServerPlayerEntity> winners) {
        if (this.gameOver) return;

        gameOver = true;
        onGameOver();

        gameHandle.resetGameScheduler();

        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        DataContainer data = getData();
        winners.forEach(data::ensureTracked);
        data.freeze();

        WinSequence winSequence = new WinSequence(gameHandle, data);
        winSequence.start(winners);
    }

    protected final boolean isGameOver() {
        return gameOver;
    }

    protected final void useScoreboardStatsSync(ScoreboardObjective objective) {
        setupScoreboardSync(source -> gameHandle.getScoreboardManager().sync(objective, source));
    }

    protected final void useScoreboardStatsSync(CustomScoreboardObjective objective) {
        setupScoreboardSync(source -> gameHandle.getScoreboardManager().sync(objective, source));
    }

    private void setupScoreboardSync(Consumer<IntScoreEventSource> sourceConsumer) {
        DataContainer data = getData();

        if (!(data instanceof IntScoreEventSource source)) {
            throw new UnsupportedOperationException("Data container not supported (IntScoreEventSource not implemented)");
        }

        sourceConsumer.accept(source);

        // initialize scores
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            data.ensureTracked(player);
        }
    }

    protected void onGameOver() {}

    protected abstract DataContainer getData();

    protected abstract void prepare();

    protected abstract void ready();
}

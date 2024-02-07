package work.lclpnet.ap2.impl.game;

import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.data.type.PlayerRefResolver;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.Set;

public abstract class DefaultGameInstance extends BaseGameInstance implements ParticipantListener {

    protected final PlayerRefResolver resolver;
    private boolean gameOver = false;

    public DefaultGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        this.resolver = new PlayerRefResolver(gameHandle.getServer().getPlayerManager());
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

        var data = getData();

        if (winner.isEmpty()) {
            winner = data.getBestSubject(resolver);
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

        var data = getData();
        winners.forEach(data::ensureTracked);
        data.freeze();

        var winSequence = new WinSequence<>(gameHandle, data, PlayerRef::create);
        winSequence.start(winners);
    }

    protected final boolean isGameOver() {
        return gameOver;
    }

    protected final void useScoreboardStatsSync(IntScoreEventSource<ServerPlayerEntity> source, ScoreboardObjective objective) {
        gameHandle.getScoreboardManager().sync(objective, source);

        initScores();
    }

    protected final void useScoreboardStatsSync(IntScoreEventSource<ServerPlayerEntity> source, CustomScoreboardObjective objective) {
        gameHandle.getScoreboardManager().sync(objective, source);

        initScores();
    }

    protected final void initScores() {
        var data = getData();

        // initialize scores
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            data.ensureTracked(player);
        }
    }

    protected void onGameOver() {}

    protected abstract DataContainer<ServerPlayerEntity, PlayerRef> getData();

    protected abstract void prepare();

    protected abstract void ready();
}

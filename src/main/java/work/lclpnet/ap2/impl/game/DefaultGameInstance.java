package work.lclpnet.ap2.impl.game;

import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.WinManagerAccess;
import work.lclpnet.ap2.api.game.WinManagerView;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.ap2.impl.game.data.type.PlayerGameWinners;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.data.type.PlayerRefResolver;

import java.util.Optional;

public abstract class DefaultGameInstance extends BaseGameInstance implements ParticipantListener, WinManagerView {

    protected final PlayerRefResolver resolver;
    protected final WinManager<ServerPlayerEntity, PlayerRef> winManager;

    public DefaultGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        this.resolver = new PlayerRefResolver(gameHandle.getServer().getPlayerManager());
        this.winManager = new WinManager<>(gameHandle, this::getData, PlayerRef::create, PlayerGameWinners::new);
    }

    @Override
    public ParticipantListener getParticipantListener() {
        return this;
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        // this will be called when a participant quits
        var participants = gameHandle.getParticipants().getAsSet();

        if (participants.size() > 1) return;

        var winner = participants.stream().findAny();

        var data = getData();

        if (winner.isEmpty()) {
            winner = data.getBestSubject(resolver);
        }

        winManager.win(winner.orElse(null));
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

    @Override
    public WinManagerAccess getWinManagerAccess() {
        return new WinManagerAccessImpl<>(winManager, Optional::of);
    }

    protected abstract DataContainer<ServerPlayerEntity, PlayerRef> getData();
}

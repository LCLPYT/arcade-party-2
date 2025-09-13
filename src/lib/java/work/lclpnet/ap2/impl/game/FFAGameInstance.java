package work.lclpnet.ap2.impl.game;

import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.WinManagerAccess;
import work.lclpnet.ap2.api.game.WinManagerView;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.stats.FFAStatsManager;
import work.lclpnet.ap2.api.stats.Stat;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.ap2.impl.game.data.type.FFAGameResult;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.data.type.PlayerRefResolver;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static work.lclpnet.ap2.api.stats.CommonStats.SCORE;

public abstract class FFAGameInstance extends BaseGameInstance implements ParticipantListener, WinManagerView {

    protected final PlayerRefResolver resolver;
    protected final WinManager<ServerPlayerEntity, PlayerRef> winManager;

    public FFAGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        this.resolver = new PlayerRefResolver(gameHandle.getServer().getPlayerManager());
        this.winManager = new WinManager<>(gameHandle, this::getData, Optional::of, PlayerRef::create, PlayerRef::create, FFAGameResult::new);
    }

    @Override
    public void start() {
        initScores();

        super.start();
    }

    @Override
    public ParticipantListener getParticipantListener() {
        return this;
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        // this will be called when a participant quits or is eliminated
        winManager.checkForLastRemaining();
    }

    public final void useScoreboardStatsSync(IntScoreEventSource<ServerPlayerEntity> source, ScoreboardObjective objective) {
        gameHandle.getScoreboardManager().sync(objective, source);

        initScores();
    }

    public final void useScoreboardStatsSync(IntScoreEventSource<ServerPlayerEntity> source, CustomScoreboardObjective objective) {
        gameHandle.getScoreboardManager().sync(objective, source);

        initScores();
    }

    protected final void initScores() {
        gameHandle.getParticipants().forEach(getData()::identityIfAbsent);
    }

    protected final FFAStatsManager createStats(IntScoreEventSource<ServerPlayerEntity> data, Stat<?>... stats) {
        var set = Stream.concat(Stream.of(SCORE), Arrays.stream(stats)).collect(Collectors.toSet());
        var manager = new FFAStatsManager(set);

        data.register((player, score) -> manager.set(player, SCORE, score));

        winManager.setStatsManager(manager);

        return manager;
    }


    @Override
    public WinManagerAccess getWinManagerAccess() {
        return new WinManagerAccessImpl<>(winManager, Optional::of, getData());
    }

    protected abstract DataContainer<ServerPlayerEntity, PlayerRef> getData();
}

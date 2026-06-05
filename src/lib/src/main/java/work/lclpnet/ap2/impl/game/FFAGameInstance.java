package work.lclpnet.ap2.impl.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.WinManagerAccess;
import work.lclpnet.ap2.api.game.WinManagerView;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.stats.FFAStatsManager;
import work.lclpnet.ap2.api.stats.Stat;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;
import work.lclpnet.ap2.game.player.ParticipantListener;
import work.lclpnet.ap2.impl.game.data.type.FFAGameResult;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.data.type.PlayerRefResolver;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static work.lclpnet.ap2.api.stats.CommonStats.Score;

public abstract class FFAGameInstance extends BaseGameInstance implements ParticipantListener, WinManagerView {

    protected final PlayerRefResolver resolver;
    protected final WinManager<ServerPlayer, PlayerRef> winManager;

    public FFAGameInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        this.resolver = new PlayerRefResolver(gameHandle.getServer().getPlayerList());

        var data = new WinManager.Data<>(this::getData, Optional::of, PlayerRef::create, PlayerRef::create, FFAGameResult::new);

        this.winManager = new WinManager<>(gameHandle, this::getMap, data);
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
    public void participantRemoved(@NotNull ServerPlayer player) {
        // this will be called when a participant quits or is eliminated
        winManager.checkForLastRemaining();
    }

    public final void useScoreboardStatsSync(IntScoreEventSource<ServerPlayer> source, Objective objective) {
        gameHandle.getScoreboardManager().sync(objective, source);

        initScores();
    }

    public final void useScoreboardStatsSync(IntScoreEventSource<ServerPlayer> source, CustomScoreboardObjective objective) {
        gameHandle.getScoreboardManager().sync(objective, source);

        initScores();
    }

    protected final void initScores() {
        gameHandle.getParticipants().forEach(getData()::identityIfAbsent);
    }

    /**
     * Creates a {@link work.lclpnet.ap2.api.stats.StatsManager} that tracks the score given by the game int score data container and additional stats.
     * @param data The int score data container. Automatically syncs the score stat.
     * @param stats The additional stats to track. Must be updated by the game implementation.
     * @return The {@link FFAStatsManager} to track stats.
     */
    protected final FFAStatsManager createStats(IntScoreEventSource<ServerPlayer> data, Stat<?>... stats) {
        var set = Stream.concat(Stream.of(Score), Arrays.stream(stats)).collect(Collectors.toCollection(LinkedHashSet::new));
        var manager = new FFAStatsManager(set);

        data.register((player, score) -> manager.set(player, Score, score));

        winManager.setStatsManager(manager);

        return manager;
    }

    @Override
    public WinManagerAccess getWinManagerAccess() {
        return new WinManagerAccessImpl<>(winManager, Optional::of, getData());
    }

    protected abstract DataContainer<ServerPlayer, PlayerRef> getData();
}

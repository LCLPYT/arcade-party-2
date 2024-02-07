package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FineTuningInstance extends DefaultGameInstance {

    static final int MELODY_COUNT = 3;
    static final int REPLAY_COOLDOWN = Ticks.seconds(5);
    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);
    private FineTuningSetup setup;
    private TuningPhase tuningPhase;

    public FineTuningInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        useSurvivalMode();  // survival is needed to left-click note blocks
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap gameMap) {
        setup = new FineTuningSetup(gameHandle, gameMap, world);
        return setup.createRooms();
    }

    @Override
    protected void prepare() {
        JSONArray json = getMap().requireProperty("room-note-blocks");

        var noteBlockLocations = FineTuningSetup.readNoteBlockLocations(json, gameHandle.getLogger());
        setup.teleportParticipants(noteBlockLocations);

        Map<UUID, FineTuningRoom> rooms = setup.getRooms();

        tuningPhase = new TuningPhase(gameHandle, rooms, data, this::startStagePhase);
        tuningPhase.init();
        tuningPhase.giveBooks();
    }

    @Override
    protected void ready() {
        tuningPhase.beginListen();
    }

    private void startStagePhase() {
        tuningPhase.unload();

        StagePhase stagePhase = new StagePhase(gameHandle, data, resolver, tuningPhase.getRecords(), getMap(),
                getWorld(), winner -> winner.ifPresentOrElse(this::win, this::winNobody));

        stagePhase.beginStage();
    }
}

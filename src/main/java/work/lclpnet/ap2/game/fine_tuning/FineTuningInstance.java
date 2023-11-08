package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.impl.game.BootstrapMapOptions;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Map;
import java.util.UUID;

public class FineTuningInstance extends DefaultGameInstance {

    static final int MELODY_COUNT = 3;
    static final int REPLAY_COOLDOWN = Ticks.seconds(5);
    private final ScoreTimeDataContainer data = new ScoreTimeDataContainer();
    private FineTuningSetup setup;
    private TuningPhase tuningPhase;
    private Map<UUID, FineTuningRoom> rooms;

    public FineTuningInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        setDefaultGameMode(GameMode.SURVIVAL);  // survival is needed to left-click note blocks
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void openMap() {
        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        mapFacade.openRandomMap(gameId, new BootstrapMapOptions((world, map) -> {
            setup = new FineTuningSetup(gameHandle, map, world);
            return setup.createRooms();
        }), this::onMapReady);
    }

    @Override
    protected void prepare() {
        JSONArray json = getMap().requireProperty("room-note-blocks");

        var noteBlockLocations = FineTuningSetup.readNoteBlockLocations(json, gameHandle.getLogger());
        setup.teleportParticipants(noteBlockLocations);

        rooms = setup.getRooms();
    }

    @Override
    protected void ready() {
        tuningPhase = new TuningPhase(gameHandle, rooms, data, this::startStagePhase);
        tuningPhase.init();
        tuningPhase.beginListen();
    }

    private void startStagePhase() {
        tuningPhase.unload();

        StagePhase stagePhase = new StagePhase(gameHandle, data, tuningPhase.getMelodies(), getMap(), getWorld(),
                winner -> winner.ifPresentOrElse(this::win, this::winNobody));

        stagePhase.beginStage();
    }
}

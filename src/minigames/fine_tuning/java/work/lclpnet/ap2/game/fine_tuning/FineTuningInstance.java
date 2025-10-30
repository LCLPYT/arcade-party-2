package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.DataContainers;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FineTuningInstance extends FFAGameInstance implements MapBootstrap {

    static final int MELODY_COUNT = 2;
    private final IntDataContainer<ServerPlayerEntity, PlayerRef> data;
    private FineTuningSetup setup;
    private TuningPhase tuningPhase;

    public FineTuningInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        data = DataContainers.finaleCompatibleScoreContainer(gameHandle, PlayerRef::create);

        useSurvivalMode();  // survival is needed to left-click note blocks
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerWorld world, @NotNull GameMap gameMap) {
        setup = new FineTuningSetup(gameHandle, gameMap, world);
        return setup.createRooms();
    }

    @Override
    protected void prepare() {
        JSONArray json = getMap().requireProperty("room-note-blocks");

        var noteBlockLocations = FineTuningSetup.readNoteBlockLocations(json, gameHandle.getLogger());
        setup.teleportParticipants(noteBlockLocations);

        Map<UUID, FineTuningRoom> rooms = setup.getRooms();

        tuningPhase = new TuningPhase(gameHandle, rooms, data, this::startStagePhase, commons(), getWorld());
        tuningPhase.init();
        tuningPhase.giveBooks();
    }

    @Override
    protected void go() {
        tuningPhase.beginListen();
    }

    private void startStagePhase() {
        tuningPhase.unload();

        var stagePhase = new StagePhase(gameHandle, tuningPhase.getRecords(), getMap(), getWorld(), winManager);

        stagePhase.beginStage();
    }
}

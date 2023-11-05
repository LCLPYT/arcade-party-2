package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.impl.game.BootstrapMapOptions;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class FineTuningInstance extends DefaultGameInstance {

    private final ScoreDataContainer data = new ScoreDataContainer();

    public FineTuningInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void openMap() {
        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        mapFacade.openRandomMap(gameId, new BootstrapMapOptions(this::createRooms), this::onMapReady);
    }

    @Override
    protected void prepare() {

    }

    @Override
    protected void ready() {

    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {

    }

    private CompletableFuture<Void> createRooms(ServerWorld world, GameMap map) {
        String schematicName = map.requireProperty("room-schematic");
        var session = ((MinecraftServerAccessor) gameHandle.getServer()).getSession();
        Path storage = session.getWorldDirectory(world.getRegistryKey());

        Path path = storage.resolve("schematics").resolve(schematicName);

        return CompletableFuture.runAsync(() -> {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Schematic does not exist at ".concat(path.toString()));
            }

            var adapter = FabricBlockStateAdapter.getInstance();
            BlockStructure structure;

            try (var in = Files.newInputStream(path)) {
                structure = SchematicFormats.SPONGE_V2.reader().read(in, adapter);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read schematic", e);
            }

            placeStructures(map, world, structure);
        }).exceptionally(throwable -> {
            gameHandle.getLogger().error("Failed to create rooms", throwable);
            return null;
        });
    }

    private void placeStructures(GameMap map, ServerWorld world, BlockStructure structure) {
        BlockPos roomStart = MapUtil.readBlockPos(map.requireProperty("room-start"));
        BlockPos roomDirection = signum(MapUtil.readBlockPos(map.requireProperty("room-direction")));

        final int rooms = gameHandle.getParticipants().getAsSet().size();
        final int width = structure.getWidth(),
                height = structure.getHeight(),
                length = structure.getWidth();

        int rz = roomDirection.getZ(), rx = roomDirection.getX(), ry = roomDirection.getY();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int i = 0; i < rooms; i++) {
            pos.set(
                    roomStart.getX() + i * rx * (width - Math.abs(rx)),
                    roomStart.getY() + i * ry * (height - Math.abs(ry)),
                    roomStart.getZ() + i * rz * (length - Math.abs(rz))
            );

            placeStructureAt(structure, world, pos);
        }
    }

    @SuppressWarnings("UseCompareMethod")
    private BlockPos signum(BlockPos blockPos) {
        int x = blockPos.getX(), y = blockPos.getY(), z = blockPos.getZ();

        return new BlockPos(
                (x < 0) ? -1 : ((x == 0) ? 0 : 1),
                (y < 0) ? -1 : ((y == 0) ? 0 : 1),
                (z < 0) ? -1 : ((z == 0) ? 0 : 1)
        );
    }

    private void placeStructureAt(BlockStructure structure, ServerWorld world, BlockPos pos) {
        var origin = structure.getOrigin();

        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();

        var adapter = FabricBlockStateAdapter.getInstance();
        BlockState air = Blocks.AIR.getDefaultState();

        BlockPos.Mutable printPos = new BlockPos.Mutable();

        for (var kibuPos : structure.getBlockPositions()) {
            printPos.set(
                    kibuPos.getX() - ox + px,
                    kibuPos.getY() - oy + py,
                    kibuPos.getZ() - oz + pz
            );

            var kibuState = structure.getBlockState(kibuPos);

            BlockState state = adapter.revert(kibuState);
            if (state == null) state = air;

            world.setBlockState(printPos, state, 0);
        }
    }
}

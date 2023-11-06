package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.GameMode;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.impl.game.BootstrapMapOptions;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.StructureUtil;
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
    private BlockPos baseSpawn;
    private Vec3i roomOffset;

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

        mapFacade.openRandomMap(gameId, new BootstrapMapOptions(this::createRooms), this::onMapReady);
    }

    @Override
    protected void prepare() {
        teleportParticipants();
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
        Vec3i roomDirection = normalize(MapUtil.readBlockPos(map.requireProperty("room-direction")));
        Vec3i spawnOffset = MapUtil.readBlockPos(map.requireProperty("room-player-spawn"));

        final int rooms = gameHandle.getParticipants().getAsSet().size();
        final int width = structure.getWidth(),
                height = structure.getHeight(),
                length = structure.getWidth();

        int rz = roomDirection.getZ(), rx = roomDirection.getX(), ry = roomDirection.getY();
        int sx = roomStart.getX(), sy = roomStart.getY(), sz = roomStart.getZ();

        baseSpawn = new BlockPos(
                sx + spawnOffset.getX(),
                sy + spawnOffset.getY(),
                sz + spawnOffset.getZ()
        );

        roomOffset = new Vec3i(
                rx * (width - Math.abs(rx)),
                ry * (height - Math.abs(ry)),
                rz * (length - Math.abs(rz))
        );

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int i = 0; i < rooms; i++) {
            pos.set(
                    sx + i * roomOffset.getX(),
                    sy + i * roomOffset.getY(),
                    sz + i * roomOffset.getZ()
            );

            StructureUtil.placeStructure(structure, world, pos);
        }
    }

    @SuppressWarnings("UseCompareMethod")
    private BlockPos normalize(BlockPos blockPos) {
        int x = blockPos.getX(), y = blockPos.getY(), z = blockPos.getZ();

        return new BlockPos(
                (x < 0) ? -1 : ((x == 0) ? 0 : 1),
                (y < 0) ? -1 : ((y == 0) ? 0 : 1),
                (z < 0) ? -1 : ((z == 0) ? 0 : 1)
        );
    }

    private void teleportParticipants() {
        ServerWorld world = getWorld();
        float yaw = MapUtil.readAngle(getMap().requireProperty("room-player-yaw"));

        double sx = baseSpawn.getX() + 0.5, sy = baseSpawn.getY(), sz = baseSpawn.getZ() + 0.5;
        int ox = roomOffset.getX(), oy = roomOffset.getY(), oz = roomOffset.getZ();

        int i = 0;

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            double x = sx + ox * i, y = sy + oy * i, z = sz + oz * i;

            player.teleport(world, x, y, z, yaw, 0.0F);

            i++;
        }
    }
}

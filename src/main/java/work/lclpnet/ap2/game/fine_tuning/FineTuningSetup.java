package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.MathUtil;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.StructureWriter;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

class FineTuningSetup {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final Map<UUID, FineTuningRoom> rooms = new HashMap<>();
    private BlockPos roomStart;
    private Vec3i roomOffset;

    public FineTuningSetup(MiniGameHandle gameHandle, GameMap map, ServerWorld world) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
    }

    CompletableFuture<Void> createRooms() {
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
        roomStart = MapUtil.readBlockPos(map.requireProperty("room-start"));

        Vec3i roomDirection = MathUtil.normalize(MapUtil.readBlockPos(map.requireProperty("room-direction")));

        final int rooms = gameHandle.getParticipants().getAsSet().size();
        final int width = structure.getWidth(),
                height = structure.getHeight(),
                length = structure.getLength();

        int rz = roomDirection.getZ(), rx = roomDirection.getX(), ry = roomDirection.getY();
        int sx = roomStart.getX(), sy = roomStart.getY(), sz = roomStart.getZ();

        roomOffset = new Vec3i(rx * (width - Math.abs(rx)),
                ry * (height - Math.abs(ry)),
                rz * (length - Math.abs(rz)));

        var pos = new BlockPos.Mutable();

        for (int i = 0; i < rooms; i++) {
            pos.set(sx + i * roomOffset.getX(),
                    sy + i * roomOffset.getY(),
                    sz + i * roomOffset.getZ());

            StructureWriter.placeStructure(structure, world, pos);
        }
    }

    static BlockPos[] readNoteBlockLocations(JSONArray noteBlockLocations, Logger logger) {
        if (noteBlockLocations.isEmpty()) {
            throw new IllegalStateException("There must be at least one note block configured");
        }

        List<BlockPos> locations = new ArrayList<>();

        for (Object obj : noteBlockLocations) {
            if (!(obj instanceof JSONArray tuple)) {
                logger.warn("Invalid note block location entry of type " + obj.getClass().getSimpleName());
                continue;
            }

            locations.add(MapUtil.readBlockPos(tuple));
        }

        return locations.toArray(BlockPos[]::new);
    }

    void teleportParticipants(Vec3i[] noteBlockLocations) {
        Vec3i spawnOffset = MapUtil.readBlockPos(map.requireProperty("room-player-pos"));
        float yaw = MapUtil.readAngle(map.requireProperty("room-player-yaw"));

        int i = 0;

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            BlockPos roomPos = roomStart.add(roomOffset.multiply(i++));

            FineTuningRoom room = new FineTuningRoom(world, roomPos, noteBlockLocations, spawnOffset, yaw);
            room.teleport(player);

            rooms.put(player.getUuid(), room);
        }
    }

    public Map<UUID, FineTuningRoom> getRooms() {
        return rooms;
    }
}

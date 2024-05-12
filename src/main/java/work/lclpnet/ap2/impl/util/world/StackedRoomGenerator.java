package work.lclpnet.ap2.impl.util.world;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A generator for room structures were the rooms are stacked in a particular direction.
 * Can be used to generate rooms for game participants.
 * @param <T> The room data type.
 */
public class StackedRoomGenerator<T> {

    private final ServerWorld world;
    private final GameMap map;
    private final Coordinates coordinates;
    private final RoomFactory<T> roomFactory;

    public StackedRoomGenerator(ServerWorld world, GameMap map, Coordinates coordinates, RoomFactory<T> roomFactory) {
        this.world = world;
        this.map = map;
        this.coordinates = coordinates;
        this.roomFactory = roomFactory;
    }

    /**
     * Generates a room for each participant and returns a mapping of participant uuid -> room
     * @param participants The participants.
     * @return A future with the room mapping.
     */
    public CompletableFuture<Result<T>> generate(Participants participants) {
        String schematicName = map.requireProperty("room-schematic");
        var session = ((MinecraftServerAccessor) world.getServer()).getSession();
        Path storage = session.getWorldDirectory(world.getRegistryKey());

        Path path = storage.resolve("schematics").resolve(schematicName);

        return readSchematic(path)
                .thenApply(structure -> placeStructures(map, world, participants.count(), structure))
                .thenApply(data -> {
                    var mapping = assignRooms(participants, data);
                    return new Result<>(mapping, data);
                });
    }

    private CompletableFuture<BlockStructure> readSchematic(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Schematic does not exist at ".concat(path.toString()));
            }

            var adapter = FabricBlockStateAdapter.getInstance();

            try (var in = Files.newInputStream(path)) {
                return SchematicFormats.SPONGE_V2.reader().read(in, adapter);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read schematic", e);
            }
        });
    }

    private GeneratorData placeStructures(GameMap map, ServerWorld world, int roomCount, BlockStructure structure) {
        BlockPos roomStart = MapUtil.readBlockPos(map.requireProperty("room-start"));

        Vec3i roomDirection = MathUtil.normalize(MapUtil.readBlockPos(map.requireProperty("room-direction")));

        final int width = structure.getWidth(),
                height = structure.getHeight(),
                length = structure.getLength();

        int rz = roomDirection.getZ(), rx = roomDirection.getX(), ry = roomDirection.getY();
        int sx = roomStart.getX(), sy = roomStart.getY(), sz = roomStart.getZ();

        Vec3i roomOffset = new Vec3i(rx * (width - Math.abs(rx)),
                ry * (height - Math.abs(ry)),
                rz * (length - Math.abs(rz)));

        var pos = new BlockPos.Mutable();

        for (int i = 0; i < roomCount; i++) {
            pos.set(sx + i * roomOffset.getX(),
                    sy + i * roomOffset.getY(),
                    sz + i * roomOffset.getZ());

            StructureUtil.placeStructureFast(structure, world, pos);
        }

        return new GeneratorData(roomStart, roomOffset, structure);
    }

    private Map<UUID, T> assignRooms(Participants participants, GeneratorData data) {
        Vec3i spawnOffset = MapUtil.readBlockPos(map.requireProperty("room-player-spawn"));
        float yaw = MapUtil.readAngle(map.requireProperty("room-player-yaw"));

        if (coordinates == Coordinates.ABSOLUTE) {
            // spawnOffset is given in absolute schematic coordinates => relativize them
            KibuBlockPos origin = data.structure().getOrigin();
            spawnOffset = spawnOffset.add(-origin.getX(), -origin.getY(), -origin.getZ());
        }

        int i = 0;

        Map<UUID, T> rooms = new HashMap<>();

        BlockPos roomStart = data.roomStart();
        Vec3i roomOffset = data.roomOffset();

        for (ServerPlayerEntity player : participants) {
            BlockPos roomPos = roomStart.add(roomOffset.multiply(i++));
            BlockPos spawn = roomPos.add(spawnOffset);

            T room = roomFactory.createRoom(roomPos, spawn, yaw, data.structure());

            rooms.put(player.getUuid(), room);
        }

        return rooms;
    }

    /**
     * A factory to create room instances.
     * @param <T> The room data type.
     */
    public interface RoomFactory<T> {

        /**
         * Create a room instance.
         * @param pos The room position in the world, e.g. where the minimum position of the room is inside the world.
         * @param spawn The room spawn position, in absolute world coordinates.
         * @param spawnYaw The spawn yaw of the room.
         * @param structure The structure the room consists of.
         * @return A room instance of the specified data type.
         */
        T createRoom(BlockPos pos, BlockPos spawn, float spawnYaw, BlockStructure structure);
    }

    public record GeneratorData(BlockPos roomStart, Vec3i roomOffset, BlockStructure structure) {}

    public record Result<T>(Map<UUID, T> rooms, GeneratorData data) {}

    public enum Coordinates {
        ABSOLUTE,
        RELATIVE
    }
}

package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

class FineTuningSetup {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final Map<UUID, FineTuningRoom> rooms = new HashMap<>();

    public FineTuningSetup(MiniGameHandle gameHandle, GameMap map, ServerWorld world) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
    }

    CompletableFuture<Void> createRooms() {
        var generator = new StackedRoomGenerator<>(world, map, StackedRoomGenerator.Coordinates.RELATIVE,
                FineTuningRoom::new, gameHandle.getLogger());

        return generator.generate(gameHandle.getParticipants()).thenAccept(this.rooms::putAll);
    }

    static BlockPos[] readNoteBlockLocations(JSONArray noteBlockLocations, Logger logger) {
        if (noteBlockLocations.isEmpty()) {
            throw new IllegalStateException("There must be at least one note block configured");
        }

        List<BlockPos> locations = new ArrayList<>();

        for (Object obj : noteBlockLocations) {
            if (!(obj instanceof JSONArray tuple)) {
                logger.warn("Invalid note block location entry of type {}", obj.getClass().getSimpleName());
                continue;
            }

            locations.add(MapUtil.readBlockPos(tuple));
        }

        return locations.toArray(BlockPos[]::new);
    }

    void teleportParticipants(Vec3i[] noteBlockLocations) {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            FineTuningRoom room = rooms.get(player.getUuid());

            if (room == null) continue;

            room.setNoteBlocks(noteBlockLocations);
            room.teleport(player, world);
        }
    }

    public Map<UUID, FineTuningRoom> getRooms() {
        return rooms;
    }
}

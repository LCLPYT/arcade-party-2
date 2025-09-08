package work.lclpnet.ap2.game.fine_tuning;

import lombok.Getter;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

class FineTuningSetup {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    @Getter
    private final Map<UUID, FineTuningRoom> rooms = new HashMap<>();

    public FineTuningSetup(MiniGameHandle gameHandle, GameMap map, ServerWorld world) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
    }

    CompletableFuture<Void> createRooms() {
        var generator = new StackedRoomGenerator<>(world, map, StackedRoomGenerator.Coordinates.RELATIVE, this::createRoom);

        return generator.generate(gameHandle.getParticipants())
                .thenApply(StackedRoomGenerator.Result::rooms)
                .thenAccept(this.rooms::putAll)
                .thenCompose(nil -> world.getServer().submit(this::setupRooms))
                .exceptionally(throwable -> {
                    gameHandle.getLogger().error("Failed to create rooms", throwable);
                    return null;
                });
    }

    private void setupRooms() {
        BlockPos testSignRelPos = MapUtil.readBlockPos(map.requireProperty("test-sign"));

        Participants participants = gameHandle.getParticipants();
        var testMsg = gameHandle.getTranslations().translateText("game.ap2.fine_tuning.test");

        for (var entry : rooms.entrySet()) {
            ServerPlayerEntity player = participants.getParticipant(entry.getKey()).orElse(null);

            if (player == null) continue;

            FineTuningRoom room = entry.getValue();
            BlockPos testSignPos = room.getPos().add(testSignRelPos);

            SignBlockEntity sign = world.getBlockEntity(testSignPos, BlockEntityType.SIGN).orElse(null);

            if (sign == null) continue;

            Text[] lines = new Text[] {Text.empty(), testMsg.translateFor(player), Text.literal("▶"), Text.empty()};
            sign.setText(new SignText(lines, lines, DyeColor.BLUE, false), true);

            room.setTestSignPos(testSignPos);
        }
    }

    private FineTuningRoom createRoom(BlockPos pos, BlockPos spawn, float spawnYaw, BlockStructure structure) {
        return new FineTuningRoom(pos, spawn, spawnYaw);
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
}

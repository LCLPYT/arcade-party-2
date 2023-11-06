package work.lclpnet.ap2.game.fine_tuning;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.GameMode;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.impl.game.BootstrapMapOptions;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.MathUtil;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.SchematicFormats;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class FineTuningInstance extends DefaultGameInstance {

    private final ScoreDataContainer data = new ScoreDataContainer();
    private final Map<UUID, FineTuningRoom> rooms = new HashMap<>();
    private boolean playersCanInteract = false;
    private BlockPos roomStart;
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
        var noteBlockLocations = readNoteBlockLocations();
        teleportParticipants(noteBlockLocations);

        Participants participants = gameHandle.getParticipants();
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            if (!playersCanInteract || !(player instanceof ServerPlayerEntity serverPlayer)
                || !participants.isParticipating(serverPlayer)) return ActionResult.FAIL;

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (!state.isOf(Blocks.NOTE_BLOCK)) return ActionResult.FAIL;

            FineTuningRoom room = rooms.get(player.getUuid());

            if (room != null) {
                room.useNoteBlock(serverPlayer, pos);
            }

            return ActionResult.FAIL;
        });

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (!playersCanInteract || !(player instanceof ServerPlayerEntity serverPlayer)
                || !participants.isParticipating(serverPlayer)) return ActionResult.FAIL;

            BlockState state = world.getBlockState(pos);

            if (!state.isOf(Blocks.NOTE_BLOCK)) return ActionResult.FAIL;

            FineTuningRoom room = rooms.get(player.getUuid());

            if (room != null) {
                room.playNoteBlock(serverPlayer, pos);
            }

            return ActionResult.FAIL;
        });
    }

    @Override
    protected void ready() {
        playersCanInteract = true;
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
        roomStart = MapUtil.readBlockPos(map.requireProperty("room-start"));

        Vec3i roomDirection = MathUtil.normalize(MapUtil.readBlockPos(map.requireProperty("room-direction")));

        final int rooms = gameHandle.getParticipants().getAsSet().size();
        final int width = structure.getWidth(),
                height = structure.getHeight(),
                length = structure.getWidth();

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

            StructureUtil.placeStructure(structure, world, pos);
        }
    }

    private Vec3i[] readNoteBlockLocations() {
        JSONArray noteBlockLocations = getMap().requireProperty("room-note-blocks");

        if (noteBlockLocations.isEmpty()) {
            throw new IllegalStateException("There must be at least one note block configured");
        }

        Logger logger = gameHandle.getLogger();

        List<Vec3i> locations = new ArrayList<>();

        for (Object obj : noteBlockLocations) {
            if (!(obj instanceof JSONArray tuple)) {
                logger.warn("Invalid note block location entry of type " + obj.getClass().getSimpleName());
                continue;
            }

            locations.add(MapUtil.readBlockPos(tuple));
        }

        return locations.toArray(Vec3i[]::new);
    }

    private void teleportParticipants(Vec3i[] noteBlockLocations) {
        GameMap map = getMap();

        Vec3i spawnOffset = MapUtil.readBlockPos(map.requireProperty("room-player-spawn"));
        float yaw = MapUtil.readAngle(map.requireProperty("room-player-yaw"));
        Vec3i signOffset = MapUtil.readBlockPos(map.requireProperty("room-sign"));

        ServerWorld world = getWorld();
        TranslationService translations = gameHandle.getTranslations();

        int i = 0;

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            BlockPos roomPos = roomStart.add(roomOffset.multiply(i++));

            FineTuningRoom room = new FineTuningRoom(world, roomPos, noteBlockLocations, spawnOffset, yaw, signOffset);
            room.setSignText(translations.translateText(player, "game.ap2.fine_tuning.replay"));
            room.teleport(player);

            rooms.put(player.getUuid(), room);
        }
    }
}

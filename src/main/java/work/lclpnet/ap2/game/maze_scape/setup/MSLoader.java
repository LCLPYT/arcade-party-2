package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.kibu.mc.BlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.api.SchematicReader;
import work.lclpnet.kibu.schematic.vanilla.VanillaStructureFormat;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MSLoader {

    private final ServerWorld world;
    private final GameMap map;
    private final Logger logger;

    public MSLoader(ServerWorld world, GameMap map, Logger logger) {
        this.map = map;
        this.world = world;
        this.logger = logger;
    }

    public CompletableFuture<Result> load() {
        return CompletableFuture.supplyAsync(() -> {
            var session = ((MinecraftServerAccessor) world.getServer()).getSession();
            Path dir = session.getWorldDirectory(world.getRegistryKey()).resolve("structures");

            var reader = VanillaStructureFormat.get(world.getServer()).reader();
            var adapter = FabricBlockStateAdapter.getInstance();

            JSONObject piecesConfig = map.requireProperty("pieces");
            String startPieceName = map.requireProperty("start-piece");

            var pieces = new WeightedList<StructurePiece>(piecesConfig.length());
            StructurePiece startPiece = null;

            for (String name : piecesConfig.keySet()) {
                Path path = dir.resolve(name + ".nbt");

                JSONObject config = piecesConfig.optJSONObject(name);

                if (config == null) {
                    logger.error("Expected JSONObject as value of piece {}", name);
                    continue;
                }

                StructurePiece piece = parsePiece(path, reader, adapter, config);

                if (piece == null) continue;

                pieces.add(piece, piece.weight());

                if (startPieceName.equals(name)) {
                    startPiece = piece;
                }
            }

            if (startPiece == null) {
                throw new IllegalStateException("Could not find start piece named %s".formatted(startPieceName));
            }

            return new Result(pieces, startPiece);
        });
    }

    @Nullable
    private StructurePiece parsePiece(Path path, SchematicReader reader, BlockStateAdapter adapter, JSONObject config) {
        if (!Files.isRegularFile(path)) return null;

        BlockStructure struct;

        try (var in = Files.newInputStream(path)) {
            struct = reader.read(in, adapter);
        } catch (IOException e) {
            logger.error("Failed to read structure piece {}", path, e);
            return null;
        }

        BVH bounds = BVH.EMPTY;  // TODO
        List<Connector3d> connectors = List.of();  // TODO

        float weight = config.optFloat("weight", 1.0f);
        int maxCount = config.optInt("max-count", -1);

        return new StructurePiece(struct, bounds, connectors, weight, maxCount);
    }

    public record Result(WeightedList<StructurePiece> pieces, StructurePiece startPiece) {}
}

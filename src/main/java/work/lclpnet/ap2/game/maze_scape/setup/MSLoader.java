package work.lclpnet.ap2.game.maze_scape.setup;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.Orientation;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.kibu.mc.KibuBlockState;
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.schematic.api.SchematicReader;
import work.lclpnet.kibu.schematic.vanilla.VanillaStructureFormat;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.math.BlockPos.asLong;

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
    private StructurePiece parsePiece(Path path, SchematicReader reader, FabricBlockStateAdapter adapter, JSONObject config) {
        if (!Files.isRegularFile(path)) return null;

        BlockStructure struct;

        try (var in = Files.newInputStream(path)) {
            struct = reader.read(in, adapter);
        } catch (IOException e) {
            logger.error("Failed to read structure piece {}", path, e);
            return null;
        }

        var wrapper = new FabricStructureWrapper(struct, adapter);

        List<Connector3d> connectors = findConnectors(struct, adapter);
        BVH bounds = buildBounds(wrapper, connectors);

        float weight = config.optFloat("weight", 1.0f);
        int maxCount = config.optInt("max-count", -1);

        return new StructurePiece(wrapper, bounds, connectors, weight, maxCount);
    }

    private List<Connector3d> findConnectors(BlockStructure struct, FabricBlockStateAdapter adapter) {
        List<Connector3d> connectors = new ArrayList<>(2);
        var origin = struct.getOrigin();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        for (var pos : struct.getBlockPositions()) {
            KibuBlockState kibuState = struct.getBlockState(pos);
            String str = kibuState.getAsString();

            if (!str.startsWith("minecraft:jigsaw")) continue;

            // found a connector
            BlockState state = adapter.revert(kibuState);

            if (state == null) {
                logger.error("Unknown block state {}", str);
                continue;
            }

            Orientation orientation = state.get(Properties.ORIENTATION);

            var localPos = new BlockPos(pos.getX() - ox, pos.getY() - oy, pos.getZ() - oz);

            connectors.add(new Connector3d(localPos, orientation));
        }

        return connectors;
    }

    private BVH buildBounds(FabricStructureWrapper wrapper, List<Connector3d> connectors) {
        BlockStructure struct = wrapper.getStructure();

        // create a structure mask with non-air blocks
        var structMask = StructureMask.nonAir(struct);
        var mask = structMask.mask();

        // close open walls at connectors
        for (Connector3d connector : connectors) {
            maskCorridor(connector, mask, wrapper);
        }

        // TODO
        return BVH.EMPTY;
    }

    @SuppressWarnings("DuplicatedCode")
    private void maskCorridor(Connector3d connector, boolean[][][] mask, FabricStructureWrapper wrapper) {
        BlockStructure struct = wrapper.getStructure();
        Orientation orientation = connector.orientation();
        BlockPos connectorPos = connector.pos();

        // flood fill plane defined by connector direction
        Vec3i normal = orientation.getFacing().getVector();
        int width = struct.getWidth(), height = struct.getHeight(), length = struct.getLength();

        Queue<BlockPos> queue = new LinkedList<>();
        LongSet visited = new LongOpenHashSet();

        BlockPos start = connectorPos.offset(orientation.getRotation());
        queue.offer(start);
        visited.add(asLong(start.getX(), start.getY(), start.getZ()));

        var plane = new Plane(connectorPos, normal);
        var transparent = new TransparencyChecker(wrapper);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            final int x = pos.getX(), y = pos.getY(), z = pos.getZ();

            // add to mask
            mask[x][y][z] = true;

            // for each neighbor pos "a", check if is in the plane and is transparent
            int ax, ay, az;

            ax = x + 1; ay = y; az = z;
            if (ax < width && plane.test(ax, ay, az) && transparent.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            ax = x - 1;
            if (ax >= 0 && plane.test(ax, ay, az) && transparent.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            ax = x; ay = y + 1;
            if (ay < height && plane.test(ax, ay, az) && transparent.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            ay = y - 1;
            if (ay >= 0 && plane.test(ax, ay, az) && transparent.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            ay = y; az = z + 1;
            if (az < length && plane.test(ax, ay, az) && transparent.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));

            az = z - 1;
            if (az >= 0 && plane.test(ax, ay, az) && transparent.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                queue.add(new BlockPos(ax, ay, az));
        }
    }

    private static class TransparencyChecker {

        final FabricStructureWrapper wrapper;
        final BlockPos.Mutable pos = new BlockPos.Mutable();

        TransparencyChecker(FabricStructureWrapper wrapper) {
            this.wrapper = wrapper;
        }

        boolean test(int x, int y, int z) {
            pos.set(x, y, z);
            BlockState state = wrapper.getBlockState(pos);
            return !state.isOpaque();
        }
    }

    private static class Plane {
        final int ox, oy, oz;
        final int nx, ny, nz;

        Plane(Vec3i origin, Vec3i normal) {
            ox = origin.getX();
            oy = origin.getY();
            oz = origin.getZ();
            nx = normal.getX();
            ny = normal.getY();
            nz = normal.getZ();
        }

        boolean test(int x, int y, int z) {
            return (x - ox) * nx + (y - oy) * ny + (z - oz) * nz == 0;
        }
    }

    public record Result(WeightedList<StructurePiece> pieces, StructurePiece startPiece) {}
}

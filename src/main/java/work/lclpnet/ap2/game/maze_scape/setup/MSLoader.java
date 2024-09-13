package work.lclpnet.ap2.game.maze_scape.setup;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Orientation;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.kibu.jnbt.CompoundTag;
import work.lclpnet.kibu.mc.KibuBlockEntity;
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
import java.util.function.Consumer;

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

            // make sure the jigsaw block has a target that is not minecraft:empty
            KibuBlockEntity kibuBlockEntity = struct.getBlockEntity(pos);

            if (kibuBlockEntity == null) continue;

            CompoundTag nbt = kibuBlockEntity.createNbt();
            String target = nbt.getString("target");

            if (target.isEmpty() || target.equals("minecraft:empty")) continue;

            // add connector
            Orientation orientation = state.get(Properties.ORIENTATION);

            var localPos = new BlockPos(pos.getX() - ox, pos.getY() - oy, pos.getZ() - oz);

            connectors.add(new Connector3d(localPos, orientation));
        }

        return connectors;
    }

    private BVH buildBounds(FabricStructureWrapper wrapper, List<Connector3d> connectors) {
        /*
        build a bounding volume hierarchy by doing following steps:
        1. create a structure mask (3d bool array) that contains every non-air block
        2. close structure connectors by using plane flood-fill at each connector
        3. use flood fill to detect blocks outside the now closed structure
        4. use greedy meshing to generate a list of boxes that compose the structure bounds
        5. join these boxes using a bounding volume hierarchy
        */
        BlockStructure struct = wrapper.getStructure();

        // create a structure mask with non-air blocks
        var structMask = StructureMask.nonAir(struct);
        var mask = structMask.mask();

        // close open walls at connectors
        for (Connector3d connector : connectors) {
            maskCorridor(connector, mask, wrapper);
        }

        maskInside(structMask);

        // use greedy meshing to obtain cuboid partition mesh of the mask
        List<BlockBox> boxes = structMask.greedyMeshing().generateBoxes();

        return BVH.build(boxes);
    }

    private void maskInside(StructureMask structMask) {
        // create mask that contains everything inside, aka everything except the outside
        final int width = structMask.width(), height = structMask.height(), length = structMask.length();
        final int xMax = width - 1, yMax = height - 1, zMax = length - 1;
        boolean[][][] wallMask = structMask.mask();

        // inside initially contains everything; the outside is removed iteratively from the mask
        boolean[][][] inside = StructureMask.fill(new boolean[width][height][length], width, height, true);

        // utilize flood fill to detect outside
        var floodFill = new BoxFloodFill(width, height, length);

        // search for air blocks on the structure bounds border that are not yet marked as outside
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    if (x != 0 && x != xMax && y != 0 && y != yMax && z != 0 && z != zMax || wallMask[x][y][z]
                        || !inside[x][y][z]) continue;

                    // found air border that is still marked as inside; initiate flood-fill
                    floodFill.execute(
                            new BlockPos(x, y, z),
                            pos -> inside[pos.getX()][pos.getY()][pos.getZ()] = false,  // mark as outside
                            (ax, ay, az) -> !wallMask[ax][ay][az]);  // add neighbour if also outside
                }
            }
        }

        // copy result to original array
        System.arraycopy(inside, 0, wallMask, 0, inside.length);
    }

    private void maskCorridor(Connector3d connector, boolean[][][] mask, FabricStructureWrapper wrapper) {
        BlockStructure struct = wrapper.getStructure();
        Orientation orientation = connector.orientation();
        BlockPos connectorPos = connector.pos();

        // flood fill plane defined by connector direction
        Vec3i normal = orientation.getFacing().getVector();
        int width = struct.getWidth(), height = struct.getHeight(), length = struct.getLength();

        BlockPos start = connectorPos.offset(orientation.getRotation());
        var plane = new Plane(connectorPos, normal);
        var transparent = new TransparencyChecker(wrapper);

        new BoxFloodFill(width, height, length).execute(
                start,
                pos -> mask[pos.getX()][pos.getY()][pos.getZ()] = true,  // add to mask
                (x, y, z) -> plane.test(x, y, z) && transparent.test(x, y, z));  // add neighbour if in plane and transparent
    }

    private interface Int3Predicate {
        boolean test(int x, int y, int z);
    }

    private static class TransparencyChecker implements Int3Predicate {

        final FabricStructureWrapper wrapper;
        final BlockPos.Mutable pos = new BlockPos.Mutable();

        TransparencyChecker(FabricStructureWrapper wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public boolean test(int x, int y, int z) {
            pos.set(x, y, z);
            BlockState state = wrapper.getBlockState(pos);
            return !state.isOpaque();
        }
    }

    private static class Plane implements Int3Predicate {
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

        @Override
        public boolean test(int x, int y, int z) {
            return (x - ox) * nx + (y - oy) * ny + (z - oz) * nz == 0;
        }
    }

    private static class BoxFloodFill {
        final Queue<BlockPos> queue = new LinkedList<>();
        final LongSet visited = new LongOpenHashSet();
        final int width, height, length;

        BoxFloodFill(int width, int height, int length) {
            this.width = width;
            this.height = height;
            this.length = length;
        }

        @SuppressWarnings("DuplicatedCode")
        synchronized void execute(BlockPos start, Consumer<BlockPos> action, Int3Predicate predicate) {
            queue.clear();
            visited.clear();

            queue.offer(start);
            visited.add(asLong(start.getX(), start.getY(), start.getZ()));

            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                action.accept(pos);

                final int x = pos.getX(), y = pos.getY(), z = pos.getZ();

                // test each neighbor pos "a" and maybe offer to the queue
                int ax, ay, az;

                ax = x + 1; ay = y; az = z;
                if (ax < width && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                    queue.add(new BlockPos(ax, ay, az));

                ax = x - 1;
                if (ax >= 0 && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                    queue.add(new BlockPos(ax, ay, az));

                ax = x; ay = y + 1;
                if (ay < height && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                    queue.add(new BlockPos(ax, ay, az));

                ay = y - 1;
                if (ay >= 0 && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                    queue.add(new BlockPos(ax, ay, az));

                ay = y; az = z + 1;
                if (az < length && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                    queue.add(new BlockPos(ax, ay, az));

                az = z - 1;
                if (az >= 0 && predicate.test(ax, ay, az) && visited.add(asLong(ax, ay, az)))
                    queue.add(new BlockPos(ax, ay, az));
            }
        }
    }

    public record Result(WeightedList<StructurePiece> pieces, StructurePiece startPiece) {}
}

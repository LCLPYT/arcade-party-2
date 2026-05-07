package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.setup.wall.ConnectorWall;
import work.lclpnet.ap2.game.maze_scape.setup.wall.PaletteConnectorWall;
import work.lclpnet.ap2.game.maze_scape.setup.wall.StructureConnectorWall;
import work.lclpnet.ap2.game.maze_scape.util.BlockPalette;
import work.lclpnet.ap2.game.maze_scape.util.BoxFloodFill;
import work.lclpnet.ap2.game.maze_scape.util.PlanePredicate;
import work.lclpnet.ap2.game.maze_scape.util.TransparencyPredicate;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate;
import work.lclpnet.gaco.ds.BVH;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.StructureMask;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.abs;
import static java.lang.Math.pow;

public class MSLoader {

    public static final boolean DEBUG_PIECES = false;

    private final ServerLevel world;
    private final GameMap map;
    private final Logger logger;
    private final FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();
    private final SchematicReader schematicReader;
    private final MSScanner scanner;

    public MSLoader(ServerLevel world, GameMap map, Logger logger) {
        this.map = map;
        this.world = world;
        this.logger = logger;
        this.schematicReader = VanillaStructureFormat.get(Objects.requireNonNull(world.getServer())).reader();
        this.scanner = new MSScanner(logger);
    }

    public CompletableFuture<Result> load() {
        return CompletableFuture.supplyAsync(this::loadSync);
    }

    private @NotNull Result loadSync() {
        var clusterDefs = parseClusterDefinitions(map.getProperty("clusters"));

        MinecraftServer server = Objects.requireNonNull(world.getServer());

        var session = ((MinecraftServerAccessor) server).getStorageSource();
        Path dir = session.getDimensionPath(world.dimension()).resolve("structures");

        JSONObject wallCfg = map.requireProperty("default-connector-wall");
        var defaultConnectorWall = parseConnectorWall(wallCfg, dir);

        JSONObject piecesConfig = map.requireProperty("pieces");
        String startPieceName = map.requireProperty("start-piece");

        var pieces = new ArrayList<StructurePiece>(piecesConfig.length());
        StructurePiece startPiece = null;

        var debugOffset = DEBUG_PIECES ? new DebugOffset() : null;

        for (String name : piecesConfig.keySet()) {
            Path path = dir.resolve(name + ".nbt");

            JSONObject config = piecesConfig.optJSONObject(name);

            if (config == null) {
                logger.error("Expected JSONObject as value of piece {}", name);
                continue;
            }

            StructurePiece piece = parsePiece(path, config, clusterDefs, debugOffset);

            if (piece == null) continue;

            pieces.add(piece);

            if (startPieceName.equals(name)) {
                startPiece = piece;
            }
        }

        if (startPiece == null) {
            throw new IllegalStateException("Could not find start piece named %s".formatted(startPieceName));
        }

        return new Result(pieces, startPiece, defaultConnectorWall);
    }

    private Map<String, ClusterDef> parseClusterDefinitions(@Nullable JSONObject rootCfg) {
        if (rootCfg == null) {
            return Map.of();
        }

        Map<String, ClusterDef> clusterDefs = new HashMap<>(rootCfg.length());

        for (String name : rootCfg.keySet()) {
            Object o = rootCfg.get(name);

            if (!(o instanceof JSONObject cfg)) {
                logger.warn("Invalid cluster definition {} expected JSONObject but got {}", name, o);
                continue;
            }

            var def = ClusterDef.fromJson(cfg, logger);

            if (def != null) {
                clusterDefs.put(name, def);
            }
        }

        return clusterDefs;
    }

    private ConnectorWall parseConnectorWall(JSONObject json, Path structureDir) {
        var array = json.optJSONArray("palette");

        if (array != null) {
            var states = BlockPalette.parseStatesFromJson(array, logger);
            BlockPalette palette = new BlockPalette(states);
            return new PaletteConnectorWall(palette);
        }

        var structName = json.optString("structure");

        if (structName != null) {
            Path path = structureDir.resolve(structName + ".nbt");

            BlockStructure struct;

            try (var in = Files.newInputStream(path)) {
                struct = schematicReader.read(in, adapter);
            } catch (IOException e) {
                logger.error("Failed to read structure piece {}", path, e);
                return ConnectorWall.EMPTY;
            }

            var connectors = scanner.scan(struct).connectors();

            return new StructureConnectorWall(struct, connectors, logger);
        }

        throw new IllegalStateException("Invalid connector wall configuration");
    }

    @Nullable
    private StructurePiece parsePiece(Path path, JSONObject config, Map<String, ClusterDef> clusterDefs, @Nullable DebugOffset debugOffset) {
        if (!Files.isRegularFile(path)) return null;

        BlockStructure struct;

        try (var in = Files.newInputStream(path)) {
            struct = schematicReader.read(in, adapter);
        } catch (IOException e) {
            logger.error("Failed to read structure piece {}", path, e);
            return null;
        }

        String name = FilenameUtils.getBaseName(path.getFileName().toString());

        @Nullable
        var debugger = debugOffset != null ? new MSPieceDebugger(world, struct, name, debugOffset.get(struct)) : null;

        if (debugger != null) debugger.debugStructure();

        // scan the structure for jigsaws
        var scanResult = scanner.scan(struct);

        var wrapper = new FabricStructureWrapper(struct, adapter);

        StructureMask insideMask = buildStructureMask(wrapper, scanResult.wallMarkers(), debugger);

        if (debugger != null) debugger.debugInsideMask(insideMask);

        BVH bounds = buildBounds(insideMask, debugger);

        float weight = config.optFloat("weight", 1.0f);
        int maxCount = config.optInt("max-count", -1);
        boolean connectSame = config.optBoolean("connect-same", true);
        int minDistance = config.optInt("min-distance", 0);
        boolean updateBlocks = config.optBoolean("update-blocks", false);
        boolean noUnstuck = config.optBoolean("no-unstuck", false);

        List<BlockBox> extraGeneratorBounds = parseExtraGeneratorBounds(config);

        Set<ClusterDef> clusters = parseClusters(path, config, clusterDefs);

        Vec3 spawnPos = Optional.ofNullable(scanResult.spawn())
                .orElseGet(() -> findSpawnPos(wrapper, insideMask));

        StructureMask pit = buildPitMask(wrapper, scanResult.pitMarkers());

        StructurePiece piece = new StructurePiece(name, wrapper, bounds, scanResult.connectors(), weight, maxCount, connectSame, clusters,
                minDistance, updateBlocks, noUnstuck, extraGeneratorBounds, spawnPos, scanResult.jigsaws(), pit);

        for (ClusterDef cluster : clusters) {
            cluster.pieces().add(piece);
        }

        return piece;
    }

    private List<BlockBox> parseExtraGeneratorBounds(JSONObject config) {
        JSONArray array = config.optJSONArray("extra-generator-bounds");

        if (array == null) return List.of();

        List<BlockBox> extraBounds = new ArrayList<>(array.length());

        for (Object o : array) {
            if (!(o instanceof JSONArray tuple)) {
                logger.warn("Unexpected array element for extra generator bounds of type {}", o != null ? o.getClass().getSimpleName() : null);
                continue;
            }

            BlockBox box = MapUtil.readBox(tuple);

            extraBounds.add(box);
        }

        return extraBounds;
    }

    private StructureMask buildPitMask(FabricStructureWrapper wrapper, List<BlockPos> pitMarkers) {
        BlockStructure struct = wrapper.getStructure();
        int width = struct.getWidth();
        int height = struct.getHeight();
        int length = struct.getLength();

        boolean[][][] mask = new boolean[width][height][length];
        var queryPos = new BlockPos.MutableBlockPos();
        BoxFloodFill floodFill = null;

        // initiate flood fill from each marker, but without ascending.
        // each position is expanded upwards greedily
        for (BlockPos pitMarker : pitMarkers) {
            if (floodFill == null) {
                floodFill = new BoxFloodFill(width, height, length);
            }

            int topY = pitMarker.getY();

            floodFill.execute(pitMarker, pos -> {
                final int x = pos.getX();
                int y = pos.getY();
                final int z = pos.getZ();

                mask[x][y][z] = true;

                // greedily expand up, until hitting a surface; flood fill isn't propagated
                for (++y; y < height; y++) {
                    if (mask[x][y][z]) break;

                    queryPos.set(x, y, z);

                    BlockState state = wrapper.getBlockState(queryPos);
                    VoxelShape shape = state.getBlockSupportShape(wrapper, queryPos);

                    if (!shape.isEmpty()) {
                        double w = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
                        double h = shape.max(Direction.Axis.Y) - shape.min(Direction.Axis.Y);
                        double l = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);

                        // surface
                        if (abs(w - 1.0) < 1e-4 && abs(l - 1.0) < 1e-4 && h > 0.4) break;
                    }

                    mask[x][y][z] = true;
                }
            }, (x, y, z) -> {
                if (y > topY || mask[x][y][z]) return false;

                queryPos.set(x, y, z);

                return !wrapper.getBlockState(queryPos).isCollisionShapeFullBlock(world, queryPos);
            });
        }

        return new StructureMask(mask, width, height, length);
    }

    @Nullable
    private Vec3 findSpawnPos(FabricStructureWrapper wrapper, StructureMask insideMask) {
        var pos = new BlockPos.MutableBlockPos();
        var walkable = new WalkableBlockPredicate(wrapper, 3);  // warden is 3 blocks tall

        int width = insideMask.width();
        int height = insideMask.height();
        int length = insideMask.length();

        double cx = width * 0.5, cz = length * 0.5;
        float verticalWeight = 0.3f;

        // find the most central walkable position
        var best = new BlockPos.MutableBlockPos();
        double bestDistanceSq = Double.MAX_VALUE;

        for (int y = 1; y < height - 1; y++) {  // no need to scan the bottom / top layer
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < length; z++) {
                    if (!insideMask.isVoxelAt(x, y, z)) continue;

                    pos.set(x, y, z);
                    BlockState state = wrapper.getBlockState(pos);

                    // skip non-air blocks and blocks that neighbour outside blocks
                    if (!state.isAir() || insideMask.isBorder(x, y, z)) continue;

                    // check if position is walkable
                    pos.setY(y);

                    if (!walkable.test(pos)) continue;

                    // calc squared distance to center; prefer lower height to avoid spawning on furniture
                    double distanceSq = pow(x + 0.5 - cx, 2) + pow(verticalWeight * y, 2) + pow(z + 0.5 - cz, 2);

                    if (distanceSq < bestDistanceSq) {
                        bestDistanceSq = distanceSq;
                        best.set(x, y, z);
                    }
                }
            }
        }

        if (Double.isInfinite(bestDistanceSq)) {
            return null;
        }

        return adjustSpawn(wrapper, insideMask, best, walkable);
    }

    private Vec3 adjustSpawn(FabricStructureWrapper wrapper, StructureMask insideMask, BlockPos spawn, WalkableBlockPredicate walkable) {
        Vec3 adjustment = Vec3.ZERO;

        // check each horizontal neighbour if there is a wall (or the outside). If yes, try to move the spawn away from it
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adj = spawn.relative(direction);

            if (!insideMask.isVoxelAt(adj.getX(), adj.getY(), adj.getZ()) ||
                !wrapper.getBlockState(adj).getCollisionShape(wrapper, adj).isEmpty()) {

                // try to adjust spawn in the opposite direction
                adjustment = adjustment.add(-direction.getStepX(), 0, -direction.getStepZ());
            }
        }

        BlockPos adjusted = BlockPos.containing(Vec3.atBottomCenterOf(spawn).add(adjustment.normalize()));

        if (insideMask.isVoxelAt(adjusted.getX(), adjusted.getY(), adjusted.getZ())
            && walkable.test(adjusted)
            && !insideMask.isBorder(adjusted.getX(), adjusted.getY(), adjusted.getZ())) {

            spawn = adjusted;
        }

        return Vec3.atBottomCenterOf(spawn);
    }

    private Set<ClusterDef> parseClusters(Path path, JSONObject config, Map<String, ClusterDef> clusterDefs) {
        JSONArray clustersArray = config.optJSONArray("clusters");

        if (clustersArray == null) {
            return Set.of();
        }

        Set<ClusterDef> clusters = new LinkedHashSet<>(clustersArray.length());

        for (Object o : clustersArray) {
            if (!(o instanceof String name)) {
                logger.warn("Invalid cluster entry {}. Expected String but got {}", path.getFileName().toString(), o);
                continue;
            }

            ClusterDef def = clusterDefs.get(name);

            if (def == null) {
                logger.warn("Unknown cluster {}", name);
                continue;
            }

            clusters.add(def);
        }

        return clusters;
    }

    private BVH buildBounds(StructureMask insideMask, @Nullable MSPieceDebugger debugger) {
        // use greedy meshing to obtain cuboid partition mesh of the inside mask
        List<BlockBox> boxes = insideMask.greedyMeshing().generateBoxes();

        if (debugger != null) debugger.debugBvhBoxes(boxes);

        return BVH.build(boxes);
    }

    private @NotNull StructureMask buildStructureMask(FabricStructureWrapper wrapper, List<WallMarker> wallMarkers, @Nullable MSPieceDebugger debugger) {
        /*
        generate a mask that separates outside and inside in these steps:
        1. create a structure mask (3d bool array) that contains every non-air block
        2. close open walls in structure by using plane flood-fill at each wall marker
        3. use flood fill to detect blocks outside the now closed structure
        */
        BlockStructure struct = wrapper.getStructure();

        // create a structure mask with non-air blocks
        var structMask = StructureMask.nonAir(struct);
        var mask = structMask.mask();

        // close open walls at connectors
        for (WallMarker wallMarker : wallMarkers) {
            maskCorridor(wallMarker, mask, wrapper);
        }

        if (debugger != null) debugger.debugClosedCorridorMask(copyMask(mask, structMask));

        // remove every outside position
        maskInside(structMask);

        return structMask;
    }

    private @NotNull StructureMask copyMask(boolean[][][] mask, StructureMask structMask) {
        boolean[][][] copy = new boolean[mask.length][][];

        for (int i = 0; i < mask.length; i++) {
            copy[i] = new boolean[mask[i].length][];

            for (int j = 0; j < mask[i].length; j++) {
                copy[i][j] = new boolean[mask[i][j].length];

                System.arraycopy(mask[i][j], 0, copy[i][j], 0, mask[i][j].length);
            }
        }

        return new StructureMask(copy, structMask.width(), structMask.height(), structMask.length());
    }

    private void maskInside(StructureMask structMask) {
        // create mask that contains everything inside, aka everything except the outside
        final int width = structMask.width(), height = structMask.height(), length = structMask.length();
        final int xMax = width - 1, yMax = height - 1, zMax = length - 1;
        boolean[][][] wallMask = structMask.mask();

        // inside initially contains everything; the outside is removed iteratively from the mask
        boolean[][][] inside = StructureMask.fill(new boolean[width][height][length], true);

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

    private void maskCorridor(WallMarker wallMarker, boolean[][][] mask, FabricStructureWrapper wrapper) {
        BlockStructure struct = wrapper.getStructure();
        FrontAndTop orientation = wallMarker.orientation();
        BlockPos connectorPos = wallMarker.pos();

        // flood fill plane defined by wall marker direction
        Vec3i normal = orientation.front().getUnitVec3i();
        int width = struct.getWidth(), height = struct.getHeight(), length = struct.getLength();

        BlockPos start = connectorPos.relative(orientation.top());
        var plane = new PlanePredicate(connectorPos, normal);
        var transparent = new TransparencyPredicate(wrapper);

        new BoxFloodFill(width, height, length).execute(
                start,
                pos -> mask[pos.getX()][pos.getY()][pos.getZ()] = true,  // add to mask
                (x, y, z) -> plane.test(x, y, z) && transparent.test(x, y, z));  // add neighbour if in plane and transparent
    }

    public record Result(
            List<StructurePiece> pieces,
            StructurePiece startPiece,
            ConnectorWall defaultConnectorWall
    ) {}

    private static class DebugOffset {
        BlockPos offset = BlockPos.ZERO;

        synchronized BlockPos get(BlockStructure struct) {
            BlockPos pos = this.offset;
            this.offset = pos.offset(0, 0, struct.getLength() + 5);
            return pos;
        }
    }
}

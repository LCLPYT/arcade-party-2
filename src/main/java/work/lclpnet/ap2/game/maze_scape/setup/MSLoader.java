package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Orientation;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.setup.wall.ConnectorWall;
import work.lclpnet.ap2.game.maze_scape.setup.wall.PaletteConnectorWall;
import work.lclpnet.ap2.game.maze_scape.setup.wall.StructureConnectorWall;
import work.lclpnet.ap2.game.maze_scape.util.*;
import work.lclpnet.ap2.impl.util.BlockBox;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MSLoader {

    private final ServerWorld world;
    private final GameMap map;
    private final Logger logger;
    private final FabricBlockStateAdapter adapter = FabricBlockStateAdapter.getInstance();
    private final SchematicReader schematicReader;

    public MSLoader(ServerWorld world, GameMap map, Logger logger) {
        this.map = map;
        this.world = world;
        this.logger = logger;
        this.schematicReader = VanillaStructureFormat.get(world.getServer()).reader();
    }

    public CompletableFuture<Result> load() {
        return CompletableFuture.supplyAsync(this::loadSync);
    }

    private @NotNull Result loadSync() {
        var clusterDefs = parseClusterDefinitions(map.getProperty("clusters"));

        var session = ((MinecraftServerAccessor) world.getServer()).getSession();
        Path dir = session.getWorldDirectory(world.getRegistryKey()).resolve("structures");

        JSONObject wallCfg = map.requireProperty("default-connector-wall");
        var defaultConnectorWall = parseConnectorWall(wallCfg, dir);

        JSONObject piecesConfig = map.requireProperty("pieces");
        String startPieceName = map.requireProperty("start-piece");

        var pieces = new ArrayList<StructurePiece>(piecesConfig.length());
        StructurePiece startPiece = null;

        for (String name : piecesConfig.keySet()) {
            Path path = dir.resolve(name + ".nbt");

            JSONObject config = piecesConfig.optJSONObject(name);

            if (config == null) {
                logger.error("Expected JSONObject as value of piece {}", name);
                continue;
            }

            StructurePiece piece = parsePiece(path, config, clusterDefs);

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

            var connectors = findConnectors(struct, adapter);

            return new StructureConnectorWall(struct, connectors, logger);
        }

        throw new IllegalStateException("Invalid connector wall configuration");
    }

    @Nullable
    private StructurePiece parsePiece(Path path, JSONObject config, Map<String, ClusterDef> clusterDefs) {
        if (!Files.isRegularFile(path)) return null;

        BlockStructure struct;

        try (var in = Files.newInputStream(path)) {
            struct = schematicReader.read(in, adapter);
        } catch (IOException e) {
            logger.error("Failed to read structure piece {}", path, e);
            return null;
        }

        var wrapper = new FabricStructureWrapper(struct, adapter);

        List<Connector3> connectors = findConnectors(struct, adapter);
        BVH bounds = buildBounds(wrapper, connectors);

        float weight = config.optFloat("weight", 1.0f);
        int maxCount = config.optInt("max-count", -1);
        boolean connectSame = config.optBoolean("connect-same", true);

        Set<ClusterDef> clusters = parseClusters(path, config, clusterDefs);
        StructurePiece piece = new StructurePiece(wrapper, bounds, connectors, weight, maxCount, connectSame, clusters);

        for (ClusterDef cluster : clusters) {
            cluster.pieces().add(piece);
        }

        return piece;
    }

    private Set<ClusterDef> parseClusters(Path path, JSONObject config, Map<String, ClusterDef> clusterDefs) {
        JSONArray clustersArray = config.optJSONArray("clusters");

        if (clustersArray == null) {
            return Set.of();
        }

        Set<ClusterDef> clusters = new HashSet<>(clustersArray.length());

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

    private BVH buildBounds(FabricStructureWrapper wrapper, List<Connector3> connectors) {
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
        for (Connector3 connector : connectors) {
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

    private void maskCorridor(Connector3 connector, boolean[][][] mask, FabricStructureWrapper wrapper) {
        BlockStructure struct = wrapper.getStructure();
        Orientation orientation = connector.orientation();
        BlockPos connectorPos = connector.pos();

        // flood fill plane defined by connector direction
        Vec3i normal = orientation.getFacing().getVector();
        int width = struct.getWidth(), height = struct.getHeight(), length = struct.getLength();

        BlockPos start = connectorPos.offset(orientation.getRotation());
        var plane = new PlanePredicate(connectorPos, normal);
        var transparent = new TransparencyPredicate(wrapper);

        new BoxFloodFill(width, height, length).execute(
                start,
                pos -> mask[pos.getX()][pos.getY()][pos.getZ()] = true,  // add to mask
                (x, y, z) -> plane.test(x, y, z) && transparent.test(x, y, z));  // add neighbour if in plane and transparent
    }

    private List<Connector3> findConnectors(BlockStructure struct, FabricBlockStateAdapter adapter) {
        List<Connector3> connectors = new ArrayList<>(2);
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
            String name = nbt.getString("name");

            if (name.isEmpty() || name.equals("minecraft:empty")) continue;

            String target = nbt.getString("target");

            if (target.isEmpty() || target.equals("minecraft:empty")) continue;

            // add connector
            Orientation orientation = state.get(Properties.ORIENTATION);

            var localPos = new BlockPos(pos.getX() - ox, pos.getY() - oy, pos.getZ() - oz);

            connectors.add(new Connector3(localPos, orientation, name, target));
        }

        return connectors;
    }

    public record Result(
            List<StructurePiece> pieces,
            StructurePiece startPiece,
            ConnectorWall defaultConnectorWall
    ) {}
}

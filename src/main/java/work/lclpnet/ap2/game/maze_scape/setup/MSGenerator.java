package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Orientation;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.world.ResetBlockWorldModifier;
import work.lclpnet.kibu.jnbt.CompoundTag;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.BlockStateUtils;
import work.lclpnet.kibu.util.RotationUtil;
import work.lclpnet.kibu.util.math.Matrix3i;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MSGenerator {

    public static final int PLACE_FLAGS = Block.FORCE_STATE | Block.SKIP_DROPS;
    private final ServerWorld world;
    private final MSLoader.Result loaded;
    private final Random random;
    private final Logger logger;
    private final Map<Connector3, ResetBlockWorldModifier> closedConnectors = new HashMap<>();
    private final int targetArea;
    private final float deadEndChance;
    private final StructureDomain domain;
    private GraphGenerator<Connector3, StructurePiece, OrientedStructurePiece> generator;

    public MSGenerator(ServerWorld world, GameMap map, MSLoader.Result loaded, Random random, Logger logger) {
        this.world = world;
        this.loaded = loaded;
        this.random = random;
        this.logger = logger;

        // the generator will continue until the total room area is greater than this value
        targetArea = ((Number) map.requireProperty("target-room-area")).intValue();

        // the generator will try to only put dead-ends after this many rooms on the path towards the start room
        int deadEndStart = ((Number) map.requireProperty("dead-end-start-level")).intValue();

        // chance to append a dead-end piece to an open connector instead of closing it
        Number deadEndChanceProp = map.requireProperty("dead-end-chance");
        deadEndChance = Math.max(0, Math.min(1, deadEndChanceProp.floatValue()));

        // this will determine the bounding box that the generated pieces must be generated in
        Number maxChunkSizeProp = map.requireProperty("max-chunk-size");
        int maxChunkSize = Math.max(maxChunkSizeProp.intValue(), 2);

        int bottomY = world.getBottomY();
        int topY = world.getTopY() - 1;

        var bounds = new StructureDomain.BoundsCfg(maxChunkSize, bottomY, topY);
        domain = new StructureDomain(loaded.pieces(), random, deadEndStart, bounds);
    }

    public synchronized boolean generate() {
        domain.reset();

        generator = new GraphGenerator<>(domain, random, logger);

        var graph = generator.generateGraph(loaded.startPiece(), g -> domain.totalArea() < targetArea).orElse(null);

        if (graph == null) {
            return false;
        }

        placeAdditionalDeadEnds(graph);

        placePieces(graph);

        return true;
    }

    private void placeAdditionalDeadEnds(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph) {
        domain.setOnlyDeadEnds(true);

        for (var node : graph.openNodes()) {
            var oriented = node.oriented();

            if (oriented == null) continue;

            var connectors = oriented.connectors();
            var children = node.children();
            int size = connectors.size();

            // place a dead end at open connectors with a certain chance
            for (int i = 0; i < size; i++) {
                if ((children != null && children.get(i) != null) || random.nextFloat() >= deadEndChance) continue;

                appendDeadEnd(oriented, i, node);
            }
        }
    }

    private void appendDeadEnd(OrientedStructurePiece oriented, int connectorIndex,
                               Graph.Node<Connector3, StructurePiece, OrientedStructurePiece> node) {

        var connectors = oriented.connectors();
        generator.initChildren(node, connectors.size());

        var connector = connectors.get(connectorIndex);
        var fitting = domain.fittingPieces(oriented, connector, node);

        if (fitting.isEmpty()) return;

        generator.placeRandomChildPiece(node, connectorIndex, fitting);
    }

    private void placePieces(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph) {
        graph.root().traverse(this::placeNode);
    }

    private boolean placeNode(Graph.Node<Connector3, StructurePiece, OrientedStructurePiece> node) {
        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) {
            logger.error("No piece for node at level {}", node.level());
            return false;
        }

        FabricStructureWrapper wrapper = oriented.piece().wrapper();
        BlockStructure struct = wrapper.getStructure();
        BlockPos pos = oriented.pos();
        Matrix3i transformation = oriented.transformation();

        StructureUtil.placeStructureFast(struct, world, pos, transformation);

        replaceJigsaws(oriented);

        closeConnectors(node, oriented);

        return true;
    }

    private void replaceJigsaws(OrientedStructurePiece oriented) {
        Matrix3i mat = oriented.transformation();
        var transformation = new AffineIntMatrix(mat, oriented.pos());

        StructurePiece piece = oriented.piece();
        BlockStructure struct = piece.wrapper().getStructure();

        var placedPos = new BlockPos.Mutable();
        var kibuPos = new KibuBlockPos.Mutable();

        for (Connector3 connector : piece.connectors()) {
            BlockPos pos = connector.pos();
            kibuPos.set(pos.getX(), pos.getY(), pos.getZ());

            // determine jigsaw final state
            var blockEntity = struct.getBlockEntity(kibuPos);

            if (blockEntity == null) {
                logger.warn("Could not find jigsaw block entity in structure at position {}", pos);
                continue;
            }

            CompoundTag nbt = blockEntity.createNbt();
            String str = nbt.getString("final_state");

            if (str.isEmpty()) continue;

            BlockState state = BlockStateUtils.parse(str);

            if (state == null) {
                logger.warn("Unknown block state {} as jigsaw final state", str);
                continue;
            }

            // apply rotation to state
            state = RotationUtil.rotate(state, mat);

            // determine actual location
            transformation.transform(pos.getX(), pos.getY(), pos.getZ(), placedPos);

            world.setBlockState(placedPos, state, PLACE_FLAGS);
        }
    }

    private void closeConnectors(Graph.Node<Connector3, StructurePiece, OrientedStructurePiece> node, OrientedStructurePiece oriented) {
        var connectors = oriented.connectors();
        var children = node.children();
        int size = connectors.size();

        if (children == null || children.size() != size) {
            // close all connectors
            for (Connector3 connector : connectors) {
                handleCloseConnector(oriented, connector);
            }
            return;
        }

        // close only open connectors
        for (int i = 0; i < size; i++) {
            var child = children.get(i);

            if (child != null) continue;

            Connector3 connector = connectors.get(i);

            handleCloseConnector(oriented, connector);
        }
    }

    private void handleCloseConnector(OrientedStructurePiece oriented, Connector3 connector) {
        // check if there is another piece, perfectly aligned with the current connector by any chance
        Orientation orientation = connector.orientation();
        Direction facing = orientation.getFacing();

        Orientation opposingOrientation = Orientation.byDirections(facing.getOpposite(), orientation.getRotation());

        if (opposingOrientation == null) return;

        BlockPos opposingPos = connector.pos().add(facing.getVector());

        // target and name must be inverted for the opposing connector
        var opposing = new Connector3(opposingPos, opposingOrientation, connector.target(), connector.name());

        var wall = closedConnectors.get(opposing);

        if (wall != null) {
            // the opposing connector was closed, remove the wall and link the two rooms
            wall.undo();
            closedConnectors.remove(opposing);
            return;
        }

        // close the connector
        ResetBlockWorldModifier modifier = new ResetBlockWorldModifier(world, PLACE_FLAGS);

        loaded.defaultConnectorWall().place(connector, oriented, modifier, random);

        closedConnectors.put(connector, modifier);
    }

    private void debugPieces() {
        var pos = new BlockPos.Mutable(0, 64, 0);

        for (StructurePiece piece : loaded.pieces()) {
            BlockStructure struct = piece.wrapper().getStructure();
            BlockBox bounds = StructureUtil.getBounds(struct);

            StructureUtil.placeStructureFast(struct, world, pos);

            pos.setX(pos.getX() + bounds.width() + 5);
        }
    }
}

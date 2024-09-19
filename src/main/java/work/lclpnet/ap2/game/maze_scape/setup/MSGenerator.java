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
    private final GameMap map;
    private final MSLoader.Result loaded;
    private final Random random;
    private final Logger logger;
    private final Map<Connector3, ResetBlockWorldModifier> closedConnectors = new HashMap<>();

    public MSGenerator(ServerWorld world, GameMap map, MSLoader.Result loaded, Random random, Logger logger) {
        this.world = world;
        this.map = map;
        this.loaded = loaded;
        this.random = random;
        this.logger = logger;
    }

    public synchronized boolean generate() {
        int deadEndStart = ((Number) map.requireProperty("dead-end-start-level")).intValue();

        Number maxChunkSizeProp = map.requireProperty("max-chunk-size");
        int maxChunkSize = Math.max(maxChunkSizeProp.intValue(), 2);

        int bottomY = world.getBottomY();
        int topY = world.getTopY() - 1;

        var domain = new StructureDomain(loaded.pieces(), random, deadEndStart, maxChunkSize, bottomY, topY);
        var generator = new GraphGenerator<>(domain, random, logger);
        var graph = generator.generateGraph(loaded.startPiece(), 100).orElse(null);

        if (graph == null) {
            return false;
        }

        placePieces(graph);

        return true;
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
        var opposing = new Connector3(opposingPos, opposingOrientation);

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

package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.wall.ConnectorWall;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.jnbt.CompoundTag;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.schematic.FabricStructureWrapper;
import work.lclpnet.kibu.structure.BlockStructure;
import work.lclpnet.kibu.util.BlockStateUtils;
import work.lclpnet.kibu.util.RotationUtil;
import work.lclpnet.kibu.util.math.Matrix3i;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class MSGenerator {

    private final ServerWorld world;
    private final GameMap map;
    private final MSLoader.Result loaded;
    private final Random random;
    private final Logger logger;

    public MSGenerator(ServerWorld world, GameMap map, MSLoader.Result loaded, Random random, Logger logger) {
        this.world = world;
        this.map = map;
        this.loaded = loaded;
        this.random = random;
        this.logger = logger;
    }

    public boolean generate() {
        var domain = new StructureDomain(loaded.pieces(), random);
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

        int flags = Block.FORCE_STATE | Block.SKIP_DROPS;

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

            world.setBlockState(placedPos, state, flags);
        }
    }

    private void closeConnectors(Graph.Node<Connector3, StructurePiece, OrientedStructurePiece> node, OrientedStructurePiece oriented) {
        var connectors = oriented.connectors();
        var children = node.children();
        int size = connectors.size();
        ConnectorWall connectorWall = loaded.defaultConnectorWall();

        if (children == null || children.size() != size) {
            // close all connectors
            for (Connector3 connector : connectors) {
                connectorWall.place(connector, oriented, world, random);
            }
            return;
        }

        // close only open connectors
        for (int i = 0; i < size; i++) {
            var child = children.get(i);

            if (child != null) continue;

            Connector3 connector = connectors.get(i);

            connectorWall.place(connector, oriented, world, random);
        }
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

package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.StructureUtil;
import work.lclpnet.kibu.structure.BlockStructure;
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

        BlockStructure struct = oriented.piece().wrapper().getStructure();
        BlockPos pos = oriented.pos();
        Matrix3i transformation = oriented.transformation();

        StructureUtil.placeStructureFast(struct, world, pos, transformation);

        return true;
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

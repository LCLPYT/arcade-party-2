package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.impl.util.ArrayUtil;

import java.util.*;

/**
 * A generator that builds a graph of interconnected materialized pieces.
 * @param <P> Piece base type that has no position or orientation in the world yet.
 * @param <O> Materialized piece type in the world. Should be a type that combines {@link P} with a position and orientation.
 */
public class GraphGenerator<C, P extends Piece<C>, O extends OrientedPiece<C, P>> {

    private final Generator<C, P, O> generator;
    private final Random random;

    public GraphGenerator(Generator<C, P, O> generator, Random random) {
        this.generator = generator;
        this.random = random;
    }

    public Graph<C, P, O> generateGraph(P startPiece, final int targetPieceCount) {
        O start = generator.placeStart(startPiece);
        var graph = new Graph<>(makeNode(start, null));
        int generatedPieces = 1;
        int currentLevel = 0;

        while (generatedPieces < targetPieceCount) {
            // first, determine all pieces that can be placed onto connectors of nodes at current level
            boolean anyFitting = false;

            var currentNodes = graph.nodesAtLevel(currentLevel);

            for (var node : currentNodes) {
                // find fitting piece for each connector
                Map<C, List<O>> fittingConnectors = findFittingConnectorPieces(node);
                node.setFittingConnectors(fittingConnectors);

                anyFitting |= fittingConnectors != null && !fittingConnectors.isEmpty();
            }

            if (!anyFitting) {
                // generation cannot be completed, as there are no possibilities left for the current graph
                // TODO try to alter tree so that generation can continue
                throw new IllegalStateException("Generation ran out of possibilities; back-tracking is not yet implemented");
            }

            // begin connector assignment for nodes in a random order
            Collections.shuffle(currentNodes, random);

            for (var node : currentNodes) {
                O oriented = node.oriented();

                if (oriented == null) continue;

                var connectors = oriented.piece().connectors();
                int connectorCount = connectors.size();
                var children = new ArrayList<Graph.Node<C, P, O>>(connectorCount);

                // mark all connectors as closed initially
                for (int i = 0; i < connectorCount; i++) {
                    children.add(null);
                }

                node.setChildren(children);

                Map<C, List<O>> fittingMap = node.fittingConnectors();

                if (fittingMap == null) {
                    // no fitting pieces set
                    continue;
                }

                // assign connectors in a random order too
                for (int i : randomIndexOrder(connectorCount)) {
                    C connector = connectors.get(i);

                    // assign a random fitting piece for the connector
                    List<O> fitting = fittingMap.get(connector);

                    if (fitting == null || fitting.isEmpty()) {
                        // no fitting pieces for the connector
                        continue;
                    }

                    O nextPiece = fitting.get(random.nextInt(fitting.size()));

                    // insert the next piece as child node
                    var nextNode = makeNode(nextPiece, node);
                    children.set(i, nextNode);
                    generatedPieces++;

                    // TODO remove all pieces of other connectors (also of other nodes) that no longer fit due to this assignment
                    // maybe lazily when getting the fitting connector list,
                    // or eagerly to save repeated checking, but would have to do more work in back-tracking
                }
            }

            // at least one new piece was assigned, therefore increment current level
            currentLevel++;
        }

        return graph;
    }

    /**
     * Generates a random permutation of [0, 1, ... , size - 1].
     * @param size The length of the permutation.
     * @return An array containing each int [0, 1, ... , size - 1] exactly once, in a random order.
     */
    private int[] randomIndexOrder(int size) {
        int[] order = new int[size];

        // order = [0, 1, 2, ... , size - 1]; order[i] -> i
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }

        ArrayUtil.shuffle(order, random);

        return order;
    }

    @Nullable
    private Map<C, List<O>> findFittingConnectorPieces(Graph.Node<C, P, O> node) {
        O placed = node.oriented();

        if (placed == null) {
            return null;
        }

        var connectors = placed.piece().connectors();
        Map<C, List<O>> fitting = new HashMap<>(connectors.size());

        for (C connector : connectors) {
            fitting.put(connector, generator.fittingPieces(connector));
        }

        return fitting;
    }

    private Graph.Node<C, P, O> makeNode(O piece, @Nullable Graph.Node<C, P, O> parent) {
        var node = new Graph.Node<C, P, O>();
        node.setOriented(piece);
        node.setParent(parent);

        return node;
    }
}

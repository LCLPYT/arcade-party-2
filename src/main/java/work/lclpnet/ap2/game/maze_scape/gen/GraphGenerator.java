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

    private final GeneratorDomain<C, P, O> domain;
    private final Random random;

    public GraphGenerator(GeneratorDomain<C, P, O> domain, Random random) {
        this.domain = domain;
        this.random = random;
    }

    public Graph<C, P, O> generateGraph(P startPiece, final int targetPieceCount) {
        O start = domain.placeStart(startPiece);
        Graph<C, P, O> graph = new Graph<>(makeNode(start, null));

        generateGraph(graph, 1, 0, targetPieceCount);

        return graph;
    }

    void generateGraph(Graph<C, P, O> graph, int generatedPieces, int currentLevel, final int targetPieceCount) {
        while (generatedPieces < targetPieceCount) {
            var currentNodes = graph.nodesAtLevel(currentLevel);

            // begin connector assignment for nodes in a random order
            Collections.shuffle(currentNodes, random);

            boolean anyPlaced = false;

            // try to expand pieces with connectors
            for (var node : currentNodes) {
                O oriented = node.oriented();

                if (oriented == null) continue;

                int connectorCount = oriented.connectors().size();

                if (connectorCount == 0) continue;

                var children = new ArrayList<Graph.Node<C, P, O>>(connectorCount);

                // mark all connectors as closed initially
                for (int i = 0; i < connectorCount; i++) {
                    children.add(null);
                }

                node.setChildren(children);

                var fittingList = findFittingConnectorPieces(oriented);

                // assign connectors in a random order too
                for (int i : randomIndexOrder(connectorCount)) {
                    // assign a random fitting piece for the connector
                    List<O> fitting = fittingList.get(i);

                    if (fitting == null || fitting.isEmpty()) {
                        // no fitting pieces for the connector
                        continue;
                    }

                    O nextPiece = fitting.get(random.nextInt(fitting.size()));

                    // insert the next piece as child node
                    var nextNode = makeNode(nextPiece, node);
                    children.set(i, nextNode);
                    generatedPieces++;
                    anyPlaced = true;

                    domain.placePiece(nextPiece);
                }
            }

            if (!anyPlaced) {
                // generation cannot be completed, as there are no possibilities left for the current graph
                // TODO try to alter tree so that generation can continue
                 throw new IllegalStateException("Generation ran out of possibilities; back-tracking is not yet implemented");
            }

            // at least one new piece was assigned, therefore increment current level
            currentLevel++;
        }
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

    public List<@Nullable List<O>> findFittingConnectorPieces(O oriented) {
        var connectors = oriented.connectors();
        List<@Nullable List<O>> fitting = new ArrayList<>(connectors.size());

        for (C connector : connectors) {
            fitting.add(domain.fittingPieces(connector));
        }

        return fitting;
    }

    public Graph.Node<C, P, O> makeNode(O piece, @Nullable Graph.Node<C, P, O> parent) {
        var node = new Graph.Node<C, P, O>();

        node.setOriented(piece);
        node.setParent(parent);

        return node;
    }
}

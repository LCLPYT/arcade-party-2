package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.util.ArrayUtil;

import java.util.*;
import java.util.function.Predicate;

/**
 * A generator that builds a graph of interconnected materialized pieces.
 * @param <C> Connector type.
 * @param <P> Piece base type that has no position or orientation in the world yet.
 * @param <O> Materialized piece type in the world. Should be a type that combines {@link P} with a position and orientation.
 * @apiNote It is expected that instances of oriented pieces {@link O} can be compared with {@link O#equals(Object)}.
 * If the equals method is not implemented accordingly with the instances returned by {@link GeneratorDomain#fittingPieces(O, Object, NodeView)},
 * the back-tracking algorithm might end up in an infinite loop.
 */
public class GraphGenerator<C, P extends Piece<C>, O extends OrientedPiece<C, P>> {

    private final GeneratorDomain<C, P, O> domain;
    private final Random random;
    private final Logger logger;

    public GraphGenerator(GeneratorDomain<C, P, O> domain, Random random, Logger logger) {
        this.domain = domain;
        this.random = random;
        this.logger = logger;
    }

    public Optional<Graph<C, P, O>> generateGraph(P startPiece, int targetPieceCount) {
        return generateGraph(startPiece, graph -> graph.nodeCount() < targetPieceCount);
    }

    public Optional<Graph<C, P, O>> generateGraph(P startPiece, Predicate<Graph<C, P, O>> condition) {
        O start = domain.placeStart(startPiece);
        Graph<C, P, O> graph = new Graph<>(makeNode(start, null));

        boolean success = generateGraph(graph, 0, condition);

        if (!success) return Optional.empty();

        return Optional.of(graph);
    }

    boolean generateGraph(Graph<C, P, O> graph, int currentLevel, Predicate<Graph<C, P, O>> condition) {
        while (condition.test(graph)) {
            var currentNodes = graph.nodesAtLevel(currentLevel);

            if (currentNodes.isEmpty()) {
                logger.error("Generator cannot find nodes to expand, aborting generation");
                return false;
            }

            // begin connector assignment for nodes in a random order
            Collections.shuffle(currentNodes, random);

            boolean anyPlaced = false;
            boolean anyLeaf = false;

            for (var node : currentNodes) {
                O oriented = node.oriented();

                if (oriented == null) {
                    logger.error("Node has no oriented piece configured, aborting generation");
                    return false;
                }

                int connectorCount = oriented.connectors().size();

                var children = node.children();

                if (children != null && children.size() == connectorCount && children.stream().allMatch(Objects::nonNull)) {
                    // if node is already fully generated, skip it; set anyPlaced=true to advance to next level
                    anyPlaced = true;
                    continue;
                }

                anyLeaf = true;

                // skip if the node has no connectors
                if (connectorCount == 0) continue;

                children = initChildren(node, connectorCount);

                var connectors = oriented.connectors();

                // assign connectors in a random order
                for (int i : randomIndexOrder(connectorCount)) {
                    // skip if connector is already assigned
                    if (children.get(i) != null) continue;

                    // find all fitting pieces for the connector
                    List<O> fitting = domain.fittingPieces(oriented, connectors.get(i), node);

                    if (fitting == null || fitting.isEmpty()) continue;

                    placeRandomChildPiece(node, i, fitting);

                    anyPlaced = true;
                }
            }

            if (anyLeaf && !anyPlaced) {
                // generation cannot be completed, as there are no possibilities left for the current graph
                // try to back-track some branch to a previous level
                OptionalInt level = performBackTracking(graph);

                if (level.isEmpty()) {
                    logger.error("Back-Tracking could not find a suitable solution; generation cannot be completed");
                    return false;
                }

                currentLevel = level.getAsInt();

                continue;
            }

            // at least one new piece was assigned, therefore increment current level
            currentLevel++;
        }

        return true;
    }

    private void placeRandomChildPiece(Graph.Node<C, P, O> node, int connectorIndex, List<O> fitting) {
        var children = node.children();

        if (children == null) return;

        O nextPiece = domain.choosePiece(fitting, random);

        // insert the next piece as child node
        var nextNode = makeNode(nextPiece, node);

        children.set(connectorIndex, nextNode);
        node.updateChildCount();

        domain.placePiece(nextPiece);
    }

    private @NotNull List<Graph.Node<C, P, O>> initChildren(Graph.Node<C, P, O> node, int connectorCount) {
        var children = node.children();

        if (children == null) {
            children = new ArrayList<>(connectorCount);
            node.setChildren(children);
        }

        for (int i = children.size(); i < connectorCount; i++) {
            children.add(null);
        }

        return children;
    }

    private OptionalInt performBackTracking(Graph<C, P, O> graph) {
        // try to back-track short paths first
        var leafNodes = graph.leafNodes();

        leafNodes.sort(Comparator.comparingInt(Graph.Node::level));

        for (var leaf : leafNodes) {
            var level = backTrackBranch(leaf, 0);

            if (level.isPresent()) {
                return level;
            }
        }

        return OptionalInt.empty();
    }

    private OptionalInt backTrackBranch(Graph.Node<C, P, O> leaf, int depth) {
        var parent = leaf.parent();

        // do not try to back-track the root node
        if (parent == null) return OptionalInt.empty();

        O oriented = leaf.oriented();

        if (oriented == null) return OptionalInt.empty();

        var children = parent.children();

        if (children == null) return OptionalInt.empty();

        int connectorIndex = children.indexOf(leaf);

        if (connectorIndex == -1) return OptionalInt.empty();

        // detach leaf
        leaf.setParent(null);
        children.set(connectorIndex, null);
        parent.updateChildCount();
        domain.removePiece(oriented);

        // now try to find a new fitting piece
        O parentOriented = parent.oriented();

        if (parentOriented == null) return OptionalInt.empty();

        var parentConnectors = parentOriented.connectors();
        C connector = parentConnectors.get(connectorIndex);

        var fitting = domain.fittingPieces(parentOriented, connector, parent);

        // remove all previous choices
        var previousChoices = parent.previousChoices(connectorIndex);
        previousChoices.add(oriented);
        fitting.removeAll(previousChoices);

        if (!fitting.isEmpty()) {
            // place random fitting piece directly
            placeRandomChildPiece(parent, connectorIndex, fitting);

            return OptionalInt.of(parent.level() + 1);
        }

        // no other piece is fitting; if the parent is a junction, stop back-tracking
        if (parentConnectors.size() >= 2) return OptionalInt.empty();

        return backTrackBranch(parent, depth + 1);
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

    public Graph.Node<C, P, O> makeNode(O piece, @Nullable Graph.Node<C, P, O> parent) {
        var node = new Graph.Node<C, P, O>();

        node.setOriented(piece);
        node.setParent(parent);

        return node;
    }
}

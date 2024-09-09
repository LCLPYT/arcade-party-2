package work.lclpnet.ap2.game.maze_scape.gen.test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.gen.GraphGenerator;

import java.util.ArrayList;
import java.util.Objects;

public class TestGraphBuilder {

    private final String2DGeneratorDomain domain;
    private final GraphGenerator<StringConnector, StringPiece, OrientedStringPiece> graphGen;

    public TestGraphBuilder(String2DGeneratorDomain domain, GraphGenerator<StringConnector, StringPiece, OrientedStringPiece> graphGen) {
        this.domain = domain;
        this.graphGen = graphGen;
    }

    public Graph.Node<StringConnector, StringPiece, OrientedStringPiece> start(StringPiece piece, int x, int y, int rotation) {
        OrientedStringPiece startPiece = new OrientedStringPiece(piece, x, y, rotation, -1);
        domain.placePiece(startPiece);

        return node(startPiece, null);
    }

    private Graph.@NotNull Node<StringConnector, StringPiece, OrientedStringPiece> node(OrientedStringPiece piece, @Nullable Graph.Node<StringConnector, StringPiece, OrientedStringPiece> parent) {
        var startNode = graphGen.makeNode(piece, parent);

        int count = piece.connectors().size();
        var children = new ArrayList<Graph.Node<StringConnector, StringPiece, OrientedStringPiece>>(count);

        for (int i = 0; i < count; i++) {
            children.add(null);
        }

        startNode.setChildren(children);

        return startNode;
    }

    public Graph.@NotNull Node<StringConnector, StringPiece, OrientedStringPiece> choose(Graph.Node<StringConnector, StringPiece, OrientedStringPiece> node, int connector, StringPiece piece, int rotation) {
        var fitting = graphGen.findFittingConnectorPieces(Objects.requireNonNull(node.oriented()));

        var childPiece = Objects.requireNonNull(fitting.get(connector)).stream()
                .filter(p -> piece.equals(p.piece()) && p.rotation() == rotation)
                .findAny().orElseThrow();

        domain.placePiece(childPiece);

        var childNode = node(childPiece, node);

        Objects.requireNonNull(node.children()).set(connector, childNode);

        return childNode;
    }
}

package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphTest {

    @Test
    void nodeCount_initial_one() {
        var graph = new Graph<>(node(null));

        // a graph needs a root node, therefore the minimum node count is one
        assertEquals(1, graph.nodeCount());
    }

    @Test
    void nodeCount_setChildren_flat() {
        var root = node(null);
        root.setChildren(List.of(node(root), node(root)));

        var graph = new Graph<>(root);

        assertEquals(3, graph.nodeCount());
    }

    @Test
    void nodeCount_setChildren_deep() {
        var root = node(null);
        var mid = node(root);

        mid.setChildren(List.of(node(mid)));
        root.setChildren(List.of(node(root), mid, node(root)));

        var graph = new Graph<>(root);

        assertEquals(5, graph.nodeCount());
    }

    @Test
    void nodeCount_setChildren_deepUpdate() {
        var root = node(null);
        var mid = node(root);
        var deep = node(mid);

        mid.setChildren(List.of(deep, node(mid)));
        root.setChildren(List.of(node(root), mid, node(root)));

        // deep update should propagate upwards
        deep.setChildren(List.of(node(deep)));

        var graph = new Graph<>(root);

        assertEquals(7, graph.nodeCount());
    }

    private Graph.Node<Object, Piece<Object>, OrientedPiece<Object, Piece<Object>>> node(
            @Nullable Graph.Node<Object, Piece<Object>, OrientedPiece<Object, Piece<Object>>> parent) {
        var node = new Graph.Node<>();
        node.setParent(parent);
        return node;
    }
}
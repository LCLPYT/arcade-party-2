package work.lclpnet.ap2.game.maze_scape.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Graph<C, P extends Piece<C>, O extends OrientedPiece<C, P>> {

    private final Node<C, P, O> root;

    public Graph(Node<C, P, O> root) {
        this.root = Objects.requireNonNull(root, "Root cannot be null");
    }

    public List<Node<C, P, O>> nodesAtLevel(int level) {
        // this can definitely be optimized e.g. by LUT
        List<Node<C, P, O>> nodes = new ArrayList<>();

        root.traverse(node -> {
            if (node.level() == level) {
                nodes.add(node);
            }

            return node.level() < level;
        });

        return nodes;
    }

    public Node<C, P, O> root() {
        return root;
    }

    public List<Node<C, P, O>> leafNodes() {
        // this can definitely be optimized by storing leaf nodes directly in a member variable
        List<Node<C, P, O>> nodes = new ArrayList<>();

        root.traverse(node -> {
            if (node.children().stream().allMatch(Objects::isNull)) {
                nodes.add(node);
            }

            return true;
        });

        return nodes;
    }

    public int nodeCount() {
        return root.deepChildCount() + 1;
    }

    public List<Node<C, P, O>> openNodes() {
        List<Node<C, P, O>> nodes = new ArrayList<>();

        root.traverse(node -> {
            if (node.children().isEmpty() || node.children().stream().anyMatch(Objects::isNull)) {
                nodes.add(node);
            }

            return true;
        });

        return nodes;
    }

    public List<Node<C,P,O>> nodes() {
        List<Node<C, P, O>> nodes = new ArrayList<>();

        root.traverse(node -> {
            nodes.add(node);

            return true;
        });

        return nodes;
    }

}

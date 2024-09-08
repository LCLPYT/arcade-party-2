package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class Graph<C, P extends Piece<C>, O extends OrientedPiece<C, P>> {

    private final Node<C, P, O> root;

    public Graph(Node<C, P, O> root) {
        this.root = Objects.requireNonNull(root, "Root cannot be null");
    }

    public List<Node<C, P, O>> nodesAtLevel(int level) {
        // this can definitely be optimized e.g. by LUT
        List<Node<C, P, O>> nodes = new ArrayList<>();

        root.traverse(node -> {
            if (node.level == level) {
                nodes.add(node);
            }

            return node.level < level;
        });

        return nodes;
    }

    public Node<C, P, O> root() {
        return root;
    }

    public static class Node<C, P extends Piece<C>, O extends OrientedPiece<C, P>> {

        private @Nullable Node<C, P, O> parent = null;
        private @Nullable List<@Nullable Node<C, P, O>> children = null;
        private @Nullable O oriented = null;
        private @Nullable List<@Nullable List<O>> fittingConnectors = null;
        private int level = 0;

        public @Nullable Node<C, P, O> parent() {
            return parent;
        }

        public void setParent(@Nullable Node<C, P, O> parent) {
            this.parent = parent;

            this.traverse(node -> {
                if (node.parent == null) node.level = 0;
                else node.level = node.parent.level + 1;

                return true;
            });
        }

        public void setOriented(@Nullable O oriented) {
            this.oriented = oriented;
        }

        public @Nullable O oriented() {
            return oriented;
        }

        public void setChildren(@Nullable List<Node<C, P, O>> children) {
            this.children = children;
        }

        public @Nullable List<Node<C, P, O>> children() {
            return children;
        }

        public void traverse(Predicate<Node<C, P, O>> action) {
            if (!action.test(this) || children == null) return;

            for (var child : children) {
                if (child != null) {
                    child.traverse(action);
                }
            }
        }

        public void setFittingConnectors(@Nullable List<@Nullable List<O>> fittingConnectors) {
            this.fittingConnectors = fittingConnectors;
        }

        public @Nullable List<@Nullable List<O>> fittingConnectors() {
            return fittingConnectors;
        }
    }
}

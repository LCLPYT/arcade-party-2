package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.NotNull;
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

    public List<Node<C, P, O>> leafNodes() {
        // this can definitely be optimized by storing leaf nodes directly in a member variable
        List<Node<C, P, O>> nodes = new ArrayList<>();

        root.traverse(node -> {
            if (node.children == null || node.children.stream().allMatch(Objects::isNull)) {
                nodes.add(node);
            }

            return true;
        });

        return nodes;
    }

    public int nodeCount() {
        return root.deepChildCount + 1;
    }

    public static class Node<C, P extends Piece<C>, O extends OrientedPiece<C, P>> implements NodeView {

        private @Nullable Node<C, P, O> parent = null;
        private @Nullable List<@Nullable Node<C, P, O>> children = null;
        private @Nullable O oriented = null;
        private @Nullable List<@Nullable List<O>> previousChoices;
        private int level = 0;
        private int deepChildCount = 0;

        public @Nullable Node<C, P, O> parent() {
            return parent;
        }

        public void setParent(@Nullable Node<C, P, O> parent) {
            this.parent = parent;

            updateLevel();
        }

        public void updateLevel() {
            this.traverse(node -> {
                if (node.parent == null) node.level = 0;
                else node.level = node.parent.level + 1;

                return true;
            });
        }

        @Override
        public int level() {
            return level;
        }

        public void setOriented(@Nullable O oriented) {
            this.oriented = oriented;
        }

        public @Nullable O oriented() {
            return oriented;
        }

        public void setChildren(@Nullable List<Node<C, P, O>> children) {
            if (this.children == children) return;

            this.children = children;

            updateChildCount();
        }

        public void updateChildCount() {
            // update deepChildCount for this node
            int prevCount = this.deepChildCount;

            this.deepChildCount = 0;

            if (children != null) {
                for (var child : children) {
                    if (child == null) continue;

                    this.deepChildCount += child.deepChildCount + 1;
                }
            }

            // update deepChildCount recursively for parent nodes
            var node = this;
            var parent = node.parent;
            int prevParentDeepChildCount;

            while (parent != null) {
                // delta update
                prevParentDeepChildCount = parent.deepChildCount;
                parent.deepChildCount = parent.deepChildCount - prevCount + node.deepChildCount;
                prevCount = prevParentDeepChildCount;
                node = parent;
                parent = node.parent;
            }
        }

        public @Nullable List<@Nullable Node<C, P, O>> children() {
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

        @NotNull
        public List<O> previousChoices(int connectorIndex) {
            if (previousChoices == null) {
                int connectorCount = oriented != null ? oriented.connectors().size() : 0;

                if (connectorCount == 0) {
                    throw new IllegalStateException("Node has no connectors");
                }

                previousChoices = new ArrayList<>(connectorCount);

                for (int i = 0; i < connectorCount; i++) {
                    previousChoices.add(null);
                }
            }

            List<O> prev = previousChoices.get(connectorIndex);

            if (prev == null) {
                prev = new ArrayList<>();
                previousChoices.set(connectorIndex, prev);
            }

            return prev;
        }
    }
}

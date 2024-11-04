package work.lclpnet.ap2.game.maze_scape.gen;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.util.DirectedGraphDfsIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class Node<C, P extends Piece<C>, O extends OrientedPiece<C, P, O>>
        implements DirectedGraphNode<Node<C, P, O>>, UndirectedGraphNode<Node<C, P, O>> {

    private final @NotNull ArrayList<@Nullable Node<C, P, O>> neighbours = new ArrayList<>();
    private final @NotNull List<@NotNull Node<C, P, O>> extraLinks = new ArrayList<>(1);
    private @NotNull List<@Nullable Node<C, P, O>> children = List.of();
    private @Nullable Node<C, P, O> parent = null;
    private @Nullable List<@Nullable List<O>> previousChoices;
    private @Nullable O oriented = null;
    private int level = 0;
    private int deepChildCount = 0;
    private int lastChildrenHash = children.hashCode();

    public @Nullable Node<C, P, O> parent() {
        return parent;
    }

    public void setParent(@Nullable Node<C, P, O> parent) {
        if (this.parent != null) {
            removeConnection(this.parent);
        }

        if (parent != null) {
            addConnection(parent);
        }

        this.parent = parent;

        updateLevel();
    }

    public void addConnection(Node<C, P, O> node) {
        extraLinks.add(node);

        updateNeighbours();
    }

    public void removeConnection(Node<C, P, O> node) {
        extraLinks.remove(node);

        updateNeighbours();
    }

    private void updateNeighbours() {
        neighbours.clear();

        neighbours.ensureCapacity(children.size() + extraLinks.size());

        neighbours.addAll(children);
        neighbours.addAll(extraLinks);

        lastChildrenHash = children.hashCode();
    }

    public void updateLevel() {
        this.traverse(node -> {
            if (node.parent == null) node.level = 0;
            else node.level = node.parent.level + 1;

            return true;
        });
    }

    public int level() {
        return level;
    }

    public int deepChildCount() {
        return deepChildCount;
    }

    public void setOriented(@Nullable O oriented) {
        if (this.oriented != null) {
            this.oriented.setNode(null);
        }

        this.oriented = oriented;

        if (this.oriented != null) {
            this.oriented.setNode(this);
        }
    }

    public @Nullable O oriented() {
        return oriented;
    }

    public void setChildren(List<Node<C, P, O>> children) {
        if (this.children == children || children == null) return;

        this.children = children;

        updateChildCount();
    }

    public void updateChildCount() {
        // update deepChildCount for this node
        int prevCount = this.deepChildCount;

        this.deepChildCount = 0;

        for (var child : children) {
            if (child == null) continue;

            this.deepChildCount += child.deepChildCount + 1;
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

    @Override
    public @NotNull List<@Nullable Node<C, P, O>> children() {
        return children;
    }

    public void traverse(Predicate<Node<C, P, O>> action) {
        if (!action.test(this)) return;

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

    public Iterable<Node<C, P, O>> iterate() {
        return () -> new DirectedGraphDfsIterator<>(this);
    }

    @Override
    public @NotNull List<@Nullable Node<C, P, O>> neighbours() {
        if (parent == null) {
            return children;
        }

        if (lastChildrenHash == children.hashCode()) {
            // no changes since last call
            return neighbours;
        }

        updateNeighbours();

        return neighbours;
    }
}

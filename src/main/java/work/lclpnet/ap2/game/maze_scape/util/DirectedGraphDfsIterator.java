package work.lclpnet.ap2.game.maze_scape.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.DirectedGraphNode;

import java.util.Iterator;
import java.util.Stack;

public class DirectedGraphDfsIterator<T extends DirectedGraphNode<T>> implements Iterator<T> {

    private final Stack<State> stack = new Stack<>();
    @Nullable private State state;
    @Nullable private T node;

    public DirectedGraphDfsIterator(@NotNull T root) {
        node = root;

        var children = root.children();

        if (!children.isEmpty()) {
            state = new State(root);
        }
    }

    @Override
    public boolean hasNext() {
        return node != null;
    }

    @Override
    public T next() {
        if (node == null) {
            return null;
        }

        var next = node;

        advance();

        return next;
    }

    private void advance() {
        if (advanceCurrent()) return;

        // pop stack until an unfinished state is found
        while (!stack.isEmpty()) {
            state = stack.pop();

            if (advanceCurrent()) return;
        }

        // no more nodes remaining
        node = null;
        state = null;
    }

    private boolean advanceCurrent() {
        T child = nextChild();

        if (child == null) return false;

        node = child;

        var grandChildren = child.children();

        if (grandChildren.isEmpty()) {
            return true;
        }

        // there are grandchildren, base the state on the new child and store old state
        stack.push(state);
        state = new State(child);

        return true;
    }

    @Nullable
    private T nextChild() {
        if (state == null) return null;

        // find next child
        var children = state.node.children();

        if (children.isEmpty()) return null;

        int size = children.size();

        if (state.childIndex >= size - 1) return null;

        // find next child
        state.childIndex++;

        for (; state.childIndex < size; state.childIndex++) {
            T child = children.get(state.childIndex);

            if (child == null) continue;

            return child;
        }

        return null;
    }

    private class State {
        final DirectedGraphNode<T> node;
        int childIndex = -1;

        State(DirectedGraphNode<T> node) {
            this.node = node;
        }
    }
}

package work.lclpnet.ap2.game.maze_scape.util;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.maze_scape.gen.DirectedGraphNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

class DirectedGraphDfsIteratorTest {

    @Test
    void iterate_sameAsInline() {
        var left = new Node(List.of(new Node(null)));
        var mid = new Node(List.of(new Node(null), new Node(List.of())));
        var right = new Node(Collections.singletonList(null));

        var root = new Node(List.of(left, mid, right));

        List<Node> inlineOrder = new ArrayList<>();
        List<Node> iteratorOrder = new ArrayList<>();

        root.traverse(inlineOrder::add);

        var it = new DirectedGraphDfsIterator<>(root);
        it.forEachRemaining(iteratorOrder::add);

        Assertions.assertEquals(inlineOrder, iteratorOrder);
    }

    private record Node(@Nullable List<@Nullable Node> children) implements DirectedGraphNode<Node> {

        public void traverse(Consumer<Node> action) {
            action.accept(this);

            if (children == null) return;

            for (@Nullable Node child : children) {
                if (child != null) {
                    child.traverse(action);
                }
            }
        }
    }
}
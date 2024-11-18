package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.ds.UndirectedGraphNode;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.hash;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PassageBuilderTest {

    @Test
    void build() {
        var a = new Node('a');
        var b = new Node('b');
        var c = new Node('c');
        var d = new Node('d');

        a.connect(b);
        a.connect(c);
        c.connect(d);

        var passageBuilder = new PassageBuilder<Node>((from, to) -> new BlockPos(hash(from.ch, to.ch), 0, 0));
        var passages = passageBuilder.build(a);

        Map<Node, Set<Passage>> expected = Map.of(
                a, passages(hash('a', 'b'), hash('a', 'c')),
                b, passages(hash('a', 'b')),
                c, passages(hash('a', 'c'), hash('c', 'd')),
                d, passages(hash('c', 'd')));

        assertEquals(expected, passages.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue()))));
    }

    private Set<Passage> passages(int... hashes) {
        return Arrays.stream(hashes)
                .mapToObj(hash -> new Passage(new BlockPos(hash, 0, 0)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static final class Node implements UndirectedGraphNode<Node> {
        final char ch;
        final List<Node> neighbours = new ArrayList<>();

        Node(char ch) {
            this.ch = ch;
        }

        void connect(Node other) {
            if (!neighbours.contains(other)) {
                neighbours.add(other);
            }

            if (!other.neighbours.contains(this)) {
                other.neighbours.add(this);
            }
        }

        @Override
        public @NotNull List<Node> neighbours() {
            return neighbours;
        }

        @Override
        public String toString() {
            return "Node(%s)".formatted(ch);
        }
    }
}
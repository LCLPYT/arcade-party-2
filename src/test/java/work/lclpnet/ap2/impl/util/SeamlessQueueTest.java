package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SeamlessQueueTest {

    public final Set<Character> pool = Set.of('a', 'b', 'c', 'd', 'e', 'f');

    @RepeatedTest(200)
    void peek_2cyclesMargin2_eachItemExactlyTwice() {
        var queue = queue(2);
        var preview = queue.peek(pool.size() * 2);

        preview.stream()
                .collect(Collectors.groupingBy(c -> c))
                .forEach((key, value) -> assertEquals(2, value.size(),
                        "Character %s occurrences".formatted(key)));
    }

    @ParameterizedTest
    @MethodSource("repeatedMargins")
    void peek_givenMargin5Cycles_marginRespected(int margin) {
        var queue = queue(margin);
        var preview = queue.peek(pool.size() * 4 + pool.size() / 2);  // 4,5 -> 5 just for fun

        assertMarginRespected(margin, preview);
    }

    @Test
    void peek_marginNMinus1_sameSequenceRepeating() {
        // sequence has no choice but to repeat when margin = pool.size() - 1
        var queue = queue(pool.size() - 1);
        var preview = queue.peek(pool.size() * 3);

        for (int i = pool.size(); i < preview.size(); i++) {
            assertEquals(preview.get(i % pool.size()), preview.get(i));
        }
    }

    @Test
    void new_emptyPool_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new SeamlessQueue<>(Set.of(), new Random(), 2, List.of()));
    }

    @Test
    void new_invalidMargin_throws() {
        assertThrows(IllegalArgumentException.class, () -> queue(pool.size()));
        assertThrows(IllegalArgumentException.class, () -> queue(pool.size() + 1));
        assertThrows(IllegalArgumentException.class, () -> queue(pool.size() + 2));
        assertThrows(IllegalArgumentException.class, () -> queue(Integer.MAX_VALUE));
    }

    @Test
    void next_respects_margin() {
        var queue = queue(3);
        var sequence = IntStream.range(0, 100).mapToObj(i -> queue.next()).toList();

        assertMarginRespected(3, sequence);
    }

    @Test
    void next_previewPrecomputed_sequenceMatchesPreview() {
        var queue = queue(2);
        var preview = queue.peek(100);
        var sequence = IntStream.range(0, 100).mapToObj(i -> queue.next()).toList();

        assertEquals(preview, sequence);
    }

    @RepeatedTest(200)
    void peek_givenHistory_marginRespected() {
        var history = List.of('a', 'd', 'c');
        var queue = new SeamlessQueue<>(pool, new Random(), 3, history);
        var preview = queue.peek(pool.size() * 2);
        var joined = Stream.concat(history.stream(), preview.stream()).toList();

        assertMarginRespected(3, joined);
    }

    private void assertMarginRespected(int margin, List<Character> sequence) {
        for (char c : pool) {
            int lastIndex = Integer.MIN_VALUE;

            for (int i = 0; i < sequence.size(); i++) {
                if (sequence.get(i) != c) continue;

                int dist = i - lastIndex;

                if (dist < 0 || dist > margin) {
                    lastIndex = i;
                    continue;
                }

                fail("Element '%s' was repeated after %s element(s), but margin was %s".formatted(c, dist, margin));
            }
        }
    }

    private static Stream<Arguments> repeatedMargins() {
        return IntStream.range(0, 100)
                .flatMap(i -> IntStream.range(0, 6))
                .mapToObj(Arguments::of);
    }

    private @NotNull SeamlessQueue<Character> queue(int margin) {
        return new SeamlessQueue<>(pool, new Random(), margin, List.of());
    }
}
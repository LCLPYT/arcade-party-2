package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import work.lclpnet.ap2.api.util.QueueTransfer;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
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

        assertEquals(pool.size() * 2, preview.size());

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
                new SeamlessQueue<>(Set.of(), new Random(), 2, QueueTransfer.empty()));
    }

    @Test
    void new_invalidMargin_throws() {
        assertThrows(IllegalArgumentException.class, () -> queue(pool.size()));
        assertThrows(IllegalArgumentException.class, () -> queue(pool.size() + 1));
        assertThrows(IllegalArgumentException.class, () -> queue(pool.size() + 2));
        assertThrows(IllegalArgumentException.class, () -> queue(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> queue(-1));
        assertThrows(IllegalArgumentException.class, () -> queue(-2));
        assertThrows(IllegalArgumentException.class, () -> queue(Integer.MIN_VALUE));
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

    @Test
    void new_givenHistory_onlyUpToMarginTaken() {
        var history = List.of('a', 'b', 'c', 'd', 'e', 'f', 'a', 'b');
        var queue = new SeamlessQueue<>(pool, new Random(), 3, new QueueTransfer<>(history, Set.of()));

        assertEquals(List.of('f', 'a', 'b'), queue.transfer().history());
    }

    @RepeatedTest(200)
    void peek_givenHistory_marginRespected() {
        var history = List.of('a', 'd', 'c');
        var queue = new SeamlessQueue<>(pool, new Random(), 3, new QueueTransfer<>(history, Set.of()));
        var preview = queue.peek(pool.size() * 2);
        var joined = Stream.concat(history.stream(), preview.stream()).toList();

        assertMarginRespected(3, joined);
    }

    @RepeatedTest(200)
    void peek_givenOccurredNoHistory_remainingPoolElementsAreSampled() {
        var occurred = Set.of('a', 'b');
        var queue = new SeamlessQueue<>(pool, new Random(), 3, new QueueTransfer<>(List.of(), occurred));
        var preview = queue.peek(pool.size() - occurred.size());

        var expected = pool.stream().filter(e -> !occurred.contains(e)).collect(Collectors.toSet());
        assertEquals(expected, new HashSet<>(preview));
    }

    @RepeatedTest(200)
    void peek_givenOccurredAndHistory_resetsOccurredIfNeeded() {
        var occurred = Set.of('a', 'b', 'c', 'd');
        var history = List.of('e', 'f');
        var queue = new SeamlessQueue<>(pool, new Random(), 3, new QueueTransfer<>(history, occurred));
        var preview = queue.peek(6);

        // occurred set leaves only 'e' and 'f', but margin excludes them with a higher priority
        assertNotEquals('e', preview.getFirst());
        assertNotEquals('f', preview.getFirst());
        assertNotEquals('e', preview.get(1));
        assertNotEquals('f', preview.get(1));

        assertMarginRespected(3, Stream.concat(history.stream(), preview.stream()).toList());
    }

    @RepeatedTest(200)
    void peek_filter_filtersQueue() {
        var history = List.of('c', 'b', 'a');
        var queue = new SeamlessQueue<>(pool, new Random(), 3, new QueueTransfer<>(history, Set.of()));

        var before = queue.peek(18);

        Predicate<Character> filter = c -> c == 'e' || c == 'f';
        queue.filter(filter);

        var expected = before.stream().filter(filter).toList();

        var after = queue.peek(6);

        assertEquals(expected, after);
    }

    @RepeatedTest(200)
    void filter_tooFewMatchingForMargin_marginIsReduced() {
        var queue = new SeamlessQueue<>(pool, new Random(), 3, new QueueTransfer<>(List.of(), Set.of()));

        Predicate<Character> filter = c -> c == 'e' || c == 'f';
        queue.filter(filter);

        var preview = queue.peek(60);

        char last = 0;

        for (int i = 0; i < 60; i++) {
            char c = preview.get(i);

            if (i > 0) {
                assertEquals(last, c == 'e' ? 'f' : 'e');
            }

            last = c;
        }
    }

    @RepeatedTest(200)
    void next_filter_resetsFutureOccurredButNotRealOccurredIfBasePoolIsNotCompleted() {
        var occurred = Set.of('a', 'b', 'c');  // leave d to not leave the base pool completed!
        var history = List.of('c', 'b', 'a');
        var queue = new SeamlessQueue<>(pool, new Random(), 2, new QueueTransfer<>(history, occurred));

        Predicate<Character> filter = c -> c == 'e' || c == 'f';
        queue.filter(filter);

        var before = queue.peek(2);
        assertEquals(Set.of('e', 'f'), Set.copyOf(before));

        before.forEach(queue::pushElement);
        assertEquals(Set.of('a', 'b', 'c', 'e', 'f'), queue.transfer().occurred());

        char next = queue.next();
        queue.pushElement(next);

        assertTrue(filter.test(next));
        assertEquals(Set.of('a', 'b', 'c', 'e', 'f'), queue.transfer().occurred());

        var after = queue.peek(4);

        after.stream()
                .collect(Collectors.groupingBy(c -> c))
                .forEach((key, value) -> assertEquals(2, value.size(),
                        "Character %s occurrences".formatted(key)));
    }

    @RepeatedTest(200)
    void next_filter_resetsRealOccurredWhenBasePoolIsCompleted() {
        var occurred = Set.of('a', 'b', 'c', 'd');
        var history = List.of('c', 'b', 'a');
        var queue = new SeamlessQueue<>(pool, new Random(), 2, new QueueTransfer<>(history, occurred));

        Predicate<Character> filter = c -> c == 'e' || c == 'f';
        queue.filter(filter);

        var before = queue.peek(2);
        assertEquals(Set.of('e', 'f'), Set.copyOf(before));

        before.forEach(queue::pushElement);
        assertEquals(Set.of(), queue.transfer().occurred());

        char next = queue.next();
        queue.pushElement(next);

        assertTrue(filter.test(next));
        assertEquals(Set.of(next), queue.transfer().occurred());

        var after = queue.peek(4);

        after.stream()
                .collect(Collectors.groupingBy(c -> c))
                .forEach((key, value) -> assertEquals(2, value.size(),
                        "Character %s occurrences".formatted(key)));
    }

    @RepeatedTest(200)
    void pushUpcoming_addsToFutureOccurred() {
        var queue = queue(0);

        queue.pushUpcoming('a');
        queue.pushUpcoming('b');
        queue.pushUpcoming('c');
        queue.pushUpcoming('d');

        var preview = queue.peek(2);

        assertEquals(Set.of('e', 'f'), new HashSet<>(preview));
    }

    @RepeatedTest(200)
    void pushUpcoming_addsToFutureHistory() {
        var queue = new SeamlessQueue<>(pool, new Random(), 3, new QueueTransfer<>(List.of('e', 'f', 'd'), Set.of()));

        queue.pushUpcoming('a');
        queue.pushUpcoming('b');
        queue.pushUpcoming('c');

        var before = List.of('a', 'b', 'c');
        var after = queue.peek(3);
        var joined = Stream.concat(before.stream(), after.stream()).toList();

        assertMarginRespected(3, joined);
        assertEquals(Set.of('d', 'e', 'f'), new HashSet<>(after));
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
        return new SeamlessQueue<>(pool, new Random(), margin, QueueTransfer.empty());
    }
}
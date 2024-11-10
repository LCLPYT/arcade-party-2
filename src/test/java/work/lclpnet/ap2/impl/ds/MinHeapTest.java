package work.lclpnet.ap2.impl.ds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class MinHeapTest {

    MinHeap<Integer> heap;
    MinHeap<String> stringHeap;

    @BeforeEach
    public void setUp() {
        heap = new MinHeap<>();
        stringHeap = new MinHeap<String>(Comparator.reverseOrder());
    }

    @Test
    public void testOfferAndPoll() {
        assertTrue(heap.offer(10));
        assertTrue(heap.offer(5));
        assertTrue(heap.offer(20));

        assertEquals(5, heap.poll());
        assertEquals(10, heap.poll());
        assertEquals(20, heap.poll());
        assertNull(heap.poll());
    }

    @Test
    public void testPeek() {
        assertNull(heap.peek());

        heap.offer(15);
        heap.offer(10);
        heap.offer(5);

        assertEquals(5, heap.peek());
        assertEquals(5, heap.peek());
    }

    @Test
    public void testContains() {
        heap.offer(30);
        heap.offer(10);
        heap.offer(20);

        assertTrue(heap.contains(10));
        assertTrue(heap.contains(20));
        assertFalse(heap.contains(25));
    }

    @Test
    public void testOfferAndPollWithComparator() {
        stringHeap.offer("apple");
        stringHeap.offer("banana");
        stringHeap.offer("cherry");

        assertEquals("cherry", stringHeap.poll());
        assertEquals("banana", stringHeap.poll());
        assertEquals("apple", stringHeap.poll());
        assertNull(stringHeap.poll());
    }

    @Test
    public void testUpdateWithMutableElement() {
        var a = new MutableElement(10);
        var b = new MutableElement(20);
        var c = new MutableElement(5);

        var heap = new MinHeap<MutableElement>();
        heap.offer(a);
        heap.offer(b);
        heap.offer(c);

        assertEquals(c, heap.peek());

        a.val = 2;
        heap.update(a);

        assertEquals(a, heap.peek());
    }

    @Test
    public void testUpdateBubbleDown() {
        var a = new MutableElement(5);
        var b = new MutableElement(10);
        var c = new MutableElement(15);

        var heap = new MinHeap<MutableElement>();
        heap.offer(a);
        heap.offer(b);
        heap.offer(c);

        assertEquals(a, heap.peek());

        a.val = 20;
        heap.update(a);

        assertEquals(b, heap.peek());
    }

    @Test
    public void testPollEmptyHeap() {
        assertNull(heap.poll());
    }

    @Test
    public void testSingleElementHeap() {
        heap.offer(42);
        assertTrue(heap.contains(42));
        assertEquals(42, heap.poll());
        assertNull(heap.poll());
    }

    @Test
    public void testUpdateWithoutChange() {
        var a = new MutableElement(5);
        var b = new MutableElement(10);

        var heap = new MinHeap<MutableElement>();
        heap.offer(a);
        heap.offer(b);

        heap.update(a);

        assertEquals(a, heap.poll());
        assertEquals(b, heap.poll());
        assertNull(heap.poll());
    }

    private static class MutableElement implements Comparable<MutableElement> {
        int val;

        public MutableElement(int value) {
            this.val = value;
        }

        @Override
        public int compareTo(MutableElement other) {
            return Integer.compare(this.val, other.val);
        }

        @Override
        public String toString() {
            return "MutableElement{val=%d}".formatted(val);
        }
    }
}
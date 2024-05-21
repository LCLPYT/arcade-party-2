package work.lclpnet.ap2.impl.util;

import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

class IndexedSetTest {

    @Test
    void testAdd() {
        var set = new IndexedSet<String>();
        assertTrue(set.add("a"));
        assertTrue(set.add("b"));
        assertFalse(set.add("a"));  // duplicate
        assertEquals(2, set.size());
    }

    @Test
    void testRemove() {
        var set = new IndexedSet<String>();
        set.add("a");
        set.add("b");
        set.add("c");
        assertTrue(set.remove("b"));
        assertFalse(set.remove("b"));  // already removed
        assertEquals(2, set.size());
    }

    @Test
    void testRemoveByIndex() {
        var set = new IndexedSet<String>();
        set.add("a");
        set.add("b");
        set.add("c");
        assertEquals("b", set.remove(1));
        assertFalse(set.contains("b"));
        assertEquals(2, set.size());
    }

    @Test
    void testContains() {
        var set = new IndexedSet<String>();
        set.add("a");
        set.add("b");
        assertTrue(set.contains("a"));
        assertFalse(set.contains("c"));
    }

    @Test
    void testGet() {
        var set = new IndexedSet<String>();
        set.add("a");
        set.add("b");
        set.add("c");
        assertEquals("a", set.get(0));
        assertEquals("b", set.get(1));
        assertEquals("c", set.get(2));
    }

    @Test
    void testIterator() {
        var set = new IndexedSet<String>();
        set.add("a");
        set.add("b");
        set.add("c");
        var iterator = set.iterator();
        assertTrue(iterator.hasNext());
        assertEquals("a", iterator.next());
        assertEquals("b", iterator.next());
        assertEquals("c", iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    void testIteratorRemove() {
        var set = new IndexedSet<String>();
        set.add("a");
        set.add("b");
        set.add("c");
        Iterator<String> iterator = set.iterator();
        iterator.next();  // "a"
        iterator.remove();
        assertFalse(set.contains("a"));
        assertEquals(2, set.size());
        assertThrows(IllegalStateException.class, iterator::remove);  // calling remove twice
    }

    @Test
    void testSize() {
        var set = new IndexedSet<String>();
        assertEquals(0, set.size());
        set.add("a");
        assertEquals(1, set.size());
        set.add("b");
        assertEquals(2, set.size());
        set.remove("a");
        assertEquals(1, set.size());
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        var set = new IndexedSet<String>();
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                set.add("n" + i);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                set.remove("n" + i);
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertTrue(set.size() <= 1000);
    }
}
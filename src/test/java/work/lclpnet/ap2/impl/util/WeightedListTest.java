package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedListTest {

    @Test
    void getRandomElement_empty_null() {
        assertNull(new WeightedList<>().getRandomElement(new Random()));
    }

    @RepeatedTest(100)
    void getRandomElement_singleElement_returned() {
        var list = new WeightedList<>();
        list.add("foo", 1f);
        assertEquals("foo", list.getRandomElement(new Random()));
    }

    @Test
    void getRandomElement_multiple_asExpected() {
        var list = new WeightedList<String>();
        list.add("foo", 0.8f);
        list.add("bar", 0.2f);

        Random random = new Random();

        final int samples = 10_000;
        var counter = new Object2IntArrayMap<String>(2);

        for (int i = 0; i < samples; i++) {
            String item = list.getRandomElement(random);
            assertNotNull(item);

            counter.computeInt(item, (key, count) -> count == null ? 1 : count + 1);
        }

        assertEquals(0.8f, counter.getOrDefault("foo", 0) / (float) samples, 10e-2);
        assertEquals(0.2f, counter.getOrDefault("bar", 0) / (float) samples, 10e-2);
    }

    @Test
    void testAdd() {
        var list = new WeightedList<String>();
        list.add("a", 1.0f);
        list.add("b", 2.0f);
        assertEquals(2, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.get(1));
    }

    @Test
    void testAddNegativeWeight() {
        var list = new WeightedList<String>();
        assertThrows(IllegalArgumentException.class, () -> list.add("a", -1.0f));
    }

    @Test
    void testRemove() {
        var list = new WeightedList<String>();
        list.add("a", 1.0f);
        list.add("b", 2.0f);
        assertEquals("a", list.removeFirst());
        assertEquals(1, list.size());
        assertEquals("b", list.getFirst());
    }

    @Test
    void testGetRandomIndex() {
        var list = new WeightedList<String>();
        Random random = new Random();
        list.add("a", 1.0f);
        list.add("b", 2.0f);
        list.add("c", 3.0f);
        int index = list.getRandomIndex(random);
        assertTrue(index >= 0 && index < list.size());
    }

    @Test
    void testMap() {
        var list = new WeightedList<String>();
        list.add("1", 1.0f);
        list.add("2", 2.0f);
        WeightedList<Integer> mappedList = list.map(Integer::parseInt);
        assertEquals(2, mappedList.size());
        assertEquals(1, mappedList.get(0));
        assertEquals(2, mappedList.get(1));
    }

    @Test
    void testSize() {
        var list = new WeightedList<String>();
        assertEquals(0, list.size());
        list.add("a", 1.0f);
        assertEquals(1, list.size());
        list.add("b", 2.0f);
        assertEquals(2, list.size());
        list.removeFirst();
        assertEquals(1, list.size());
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        var list = new WeightedList<String>();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                list.add("a" + i, i);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                if (!list.isEmpty()) {
                    list.removeFirst();
                }
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertTrue(list.size() <= 1000);
    }
}
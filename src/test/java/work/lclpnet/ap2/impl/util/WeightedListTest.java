package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedListTest {

    public static final int COUNTER_SAMPLES = 10_000;

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

        var counts = countOccurrences(list, random);

        assertPercentage(0.8f, counts, "foo");
        assertPercentage(0.2f, counts, "bar");
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
    void testMapMutable() {
        var list = new WeightedList<String>();
        list.add("1", 1.0f);
        list.add("2", 2.0f);
        var mappedList = list.map(Integer::parseInt);
        assertDoesNotThrow(() -> mappedList.add(3, 3.0f));
    }

    @Test
    void testFilter() {
        var list = new WeightedList<String>();
        list.add("hello", 1.0f);
        list.add("foo", 2.0f);
        list.add("world", 1.0f);
        var filtered = list.filter(s -> s.length() > 3);
        assertEquals(2, filtered.size());
        assertEquals("hello", filtered.getFirst());
        assertEquals("world", filtered.getLast());
    }

    @Test
    void testFilterMutable() {
        var list = new WeightedList<String>();
        list.add("hello", 1.0f);
        list.add("foo", 2.0f);
        list.add("world", 1.0f);
        var filtered = list.filter(s -> s.length() > 3);
        assertDoesNotThrow(() -> filtered.add("bar", 3.0f));
    }

    @Test
    void testFilterWeightsAsExpected() {
        var list = new WeightedList<String>();
        list.add("hello", 1.0f);
        list.add("foo", 2.0f);
        list.add("world", 2.0f);
        list.add("test", 1.0f);
        var filtered = list.filter(s -> s.length() > 3);

        var random = new Random();
        var counts = countOccurrences(filtered, random);

        assertPercentage(0.25f, counts, "hello");
        assertPercentage(0.25f, counts, "test");
        assertPercentage(0.5f, counts, "world");
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

    @Test
    void testBinarySearchCumulative() {
        float max = 5.25f;
        var cumulativeList = new FloatArrayList(new float[] {0.5f, 1.0f, 1.12f, 1.45f, 2.0f, 3.0f, max});
        float query = 0.0f;

        while (query <= max) {
            int expected = -1;

            for (int i = 0; i < cumulativeList.size(); i++) {
                float start = i > 0 ? cumulativeList.getFloat(i - 1): 0f;
                float end = cumulativeList.getFloat(i);

                if (query > start && query <= end) {
                    expected = i;
                    break;
                }
            }

            int actual = WeightedList.binarySearch(cumulativeList, query);

            assertEquals(expected, actual);

            query += 0.1f;
        }
    }

    private static Object2IntArrayMap<String> countOccurrences(WeightedList<String> list, Random random) {
        var counter = new Object2IntArrayMap<String>(2);

        for (int i = 0; i < COUNTER_SAMPLES; i++) {
            String item = list.getRandomElement(random);
            assertNotNull(item);

            counter.computeInt(item, (key, count) -> count == null ? 1 : count + 1);
        }
        return counter;
    }

    private static void assertPercentage(float expected, Object2IntArrayMap<String> counts, String hello) {
        assertEquals(expected, counts.getOrDefault(hello, 0) / (float) COUNTER_SAMPLES, 10e-2);
    }
}
package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedListTest {

    @Test
    void getRandomElement_empty_null() {
        var list = new WeightedList<>();
        assertNull(list.getRandomElement(new Random()));
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
}
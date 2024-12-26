package work.lclpnet.ap2.impl.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefCountedTest {

    private RefCounted<String, String> refCounted;
    private Map<String, String> backingMap;

    @BeforeEach
    public void setUp() {
        // store backing map for testing purposes, never do this in a non-test context
        backingMap = new HashMap<>();
        refCounted = new RefCounted<>(() -> backingMap);
    }

    @Test
    public void testReferenceIncreasesCountAndAddsValue() {
        String key = "key1";
        String value = "value1";
        Function<String, String> valueFunction = k -> value;

        String returnedValue = refCounted.reference(key, valueFunction);

        assertEquals(value, returnedValue);
        assertEquals(value, backingMap.get(key));
    }

    @Test
    public void testReferenceIncreasesCountWithoutAddingNewValueIfExists() {
        String key = "key1";
        String value = "value1";
        backingMap.put(key, value);  // FYI: never modify the map externally outside of unit tests
        Function<String, String> valueFunction = mock();

        String returnedValue = refCounted.reference(key, valueFunction);

        assertEquals(value, returnedValue);
        assertEquals(1, backingMap.size());
        assertEquals(value, backingMap.get(key));
        verify(valueFunction, never()).apply(anyString());
    }

    @Test
    public void testDereferenceDecreasesCountAndRemovesValueWhenCountIsZero() {
        String key = "key1";
        String value = "value1";
        Function<String, String> valueFunction = k -> value;

        refCounted.reference(key, valueFunction);  // count 1
        refCounted.reference(key, valueFunction);  // count 2

        refCounted.dereference(key);  // count 1
        refCounted.dereference(key);  // count 0, should remove

        assertNull(backingMap.get(key));
    }

    @Test
    public void testDereferenceDoesNotRemoveValueIfCountIsGreaterThanZero() {
        String key = "key1";
        String value = "value1";
        Function<String, String> valueFunction = k -> value;

        refCounted.reference(key, valueFunction);  // count 1
        refCounted.reference(key, valueFunction);  // count 2

        refCounted.dereference(key);  // count 1

        assertEquals(value, backingMap.get(key));
    }

    @Test
    public void testDereferenceOnNonExistingKeyDoesNothing() {
        refCounted.dereference("nonExistingKey");

        assertTrue(backingMap.isEmpty());
    }

    @RepeatedTest(10)
    public void testConcurrentReferenceAndDereference() throws InterruptedException {
        String key = "key1";
        String value = "value1";
        Function<String, String> valueFunction = k -> value;

        Thread thread1 = Thread.startVirtualThread(() -> refCounted.reference(key, valueFunction));
        Thread thread2 = Thread.startVirtualThread(() -> refCounted.dereference(key));

        thread1.join();
        thread2.join();

        // regardless of execution order, the state should be consistent
        if (backingMap.containsKey(key)) {
            assertEquals(1, refCounted.refCount.getInt(key));
        } else {
            assertFalse(refCounted.refCount.containsKey(key));
        }
    }
}
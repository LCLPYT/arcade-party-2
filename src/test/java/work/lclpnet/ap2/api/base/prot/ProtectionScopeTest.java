package work.lclpnet.ap2.api.base.prot;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionScopeTest {

    @Test
    void union() {
        var number = new AtomicInteger();

        ProtectionScope even = entity -> number.get() % 2 == 0;
        ProtectionScope divisibleBy3 = entity -> number.get() % 3 == 0;

        ProtectionScope union = ProtectionScope.union(even, divisibleBy3);

        number.set(4);
        assertTrue(union.isWithinScope(null));

        number.set(3);
        assertTrue(union.isWithinScope(null));

        number.set(6);
        assertTrue(union.isWithinScope(null));

        number.set(5);
        assertFalse(union.isWithinScope(null));
    }

    @Test
    void intersect() {
        var number = new AtomicInteger();

        ProtectionScope even = entity -> number.get() % 2 == 0;
        ProtectionScope divisibleBy3 = entity -> number.get() % 3 == 0;

        ProtectionScope intersection = ProtectionScope.intersect(even, divisibleBy3);

        number.set(4);
        assertFalse(intersection.isWithinScope(null));

        number.set(3);
        assertFalse(intersection.isWithinScope(null));

        number.set(6);
        assertTrue(intersection.isWithinScope(null));

        number.set(5);
        assertFalse(intersection.isWithinScope(null));
    }
}
package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.game.data.DataEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EliminationDataContainerTest {

    @Test
    void streamOrderedEntries() {
        var data = new EliminationDataContainer<>(StringRef::new);
        data.add("foo");
        data.addAll(List.of("bar", "baz"));
        data.add("test");

        var order = data.streamOrderedEntries()
                .map(DataEntry::subject)
                .map(StringRef::name)
                .toList();

        assertEquals(4, order.size());

        assertEquals("test", order.getFirst());
        assertTrue("bar".equals(order.get(1)) && "baz".equals(order.get(2))
                   || "bar".equals(order.get(2)) && "baz".equals(order.get(1)));
        assertEquals("foo", order.getLast());
    }

    @Test
    void streamOrderedEntries_sameEntryInstance() {
        var data = new EliminationDataContainer<>(StringRef::new);
        data.add("foo");
        data.addAll(List.of("bar", "baz"));
        data.add("test");

        var foo = data.getEntry("foo").orElseThrow();
        var bar = data.getEntry("bar").orElseThrow();
        var baz = data.getEntry("baz").orElseThrow();
        var test = data.getEntry("test").orElseThrow();

        var order = data.streamOrderedEntries().toList();

        assertSame(test, order.getFirst());
        assertTrue(bar == order.get(1) && baz == order.get(2)
                   || bar == order.get(2) && baz == order.get(1));
        assertSame(foo, order.getLast());
    }
}

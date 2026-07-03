package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.data.DataEntry;
import work.lclpnet.ap2.game.data.EliminationDataContainer;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class EliminationDataContainerTest {

    @Test
    void streamOrderedEntries() {
        var data = new EliminationDataContainer<>(StringRef::new);
        data.add("foo");
        data.addAll(List.of("bar", "baz"));
        data.add("test");

        var order = data.streamOrderedEntries()
                .map(DataEntry::getSubject)
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

        var foo = Objects.requireNonNull(data.getEntry("foo"));
        var bar = Objects.requireNonNull(data.getEntry("bar"));
        var baz = Objects.requireNonNull(data.getEntry("baz"));
        var test = Objects.requireNonNull(data.getEntry("test"));

        var order = data.streamOrderedEntries().toList();

        assertSame(test, order.getFirst());
        assertTrue(bar == order.get(1) && baz == order.get(2)
                   || bar == order.get(2) && baz == order.get(1));
        assertSame(foo, order.getLast());
    }
}

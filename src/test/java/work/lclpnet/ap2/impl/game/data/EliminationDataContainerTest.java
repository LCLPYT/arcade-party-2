package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.game.data.DataEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EliminationDataContainerTest {

    @Test
    void orderedEntries() {
        var data = new EliminationDataContainer<>(StringRef::new);
        data.eliminated("foo");
        data.allEliminated(List.of("bar", "baz"));
        data.eliminated("test");

        var order = data.orderedEntries()
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
    void orderedEntries_sameEntryInstance() {
        var data = new EliminationDataContainer<>(StringRef::new);
        data.eliminated("foo");
        data.allEliminated(List.of("bar", "baz"));
        data.eliminated("test");

        var foo = data.getEntry("foo");
        var bar = data.getEntry("bar");
        var baz = data.getEntry("baz");
        var test = data.getEntry("test");

        var order = data.orderedEntries().toList();

        assertSame(test, order.getFirst());
        assertTrue(bar == order.get(1) && baz == order.get(2)
                   || bar == order.get(2) && baz == order.get(1));
        assertSame(foo, order.getLast());
    }
}

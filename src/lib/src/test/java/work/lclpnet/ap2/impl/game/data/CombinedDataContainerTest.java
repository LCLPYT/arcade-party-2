package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.data.CombinedDataContainer;
import work.lclpnet.ap2.game.data.DataEntry;
import work.lclpnet.ap2.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.game.data.OrderedDataContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombinedDataContainerTest {

    @Test
    void getEntry_fromChildren() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new IntScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        second.addScore("bar", 7);

        first.add("foo");

        assertNotNull(container.getEntry("foo"));
        assertNotNull(container.getEntry("bar"));
    }

    @Test
    void streamOrderedEntries_inOrderOfChildrenAndWithoutDuplicates() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new IntScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        second.addScore("foo", 5);
        second.addScore("bar", 7);

        first.add("foo");

        var order = container.streamOrderedEntries()
                .map(DataEntry::getSubject)
                .map(StringRef::name)
                .toList();

        assertEquals(List.of("foo", "bar"), order);
    }

    @Test
    void identityIfAbsent_twoChildren_addedToLast() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new IntScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        container.identityIfAbsent("foo");
        container.identityIfAbsent("bar");
        container.identityIfAbsent("baz");

        assertNull(first.getEntry("foo"));
        assertNull(first.getEntry("bar"));
        assertNull(first.getEntry("baz"));

        assertNotNull(second.getEntry("foo"));
        assertNotNull(second.getEntry("bar"));
        assertNotNull(second.getEntry("baz"));
    }

    @Test
    void clear() {
    }
}
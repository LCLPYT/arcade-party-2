package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.game.data.DataEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombinedDataContainerTest {

    @Test
    void getEntry_fromChildren() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new IntScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        second.addScore("bar", 7);

        first.add("foo");

        assertTrue(container.getEntry("foo").isPresent());
        assertTrue(container.getEntry("bar").isPresent());
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
                .map(DataEntry::subject)
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

        assertTrue(first.getEntry("foo").isEmpty());
        assertTrue(first.getEntry("bar").isEmpty());
        assertTrue(first.getEntry("baz").isEmpty());

        assertTrue(second.getEntry("foo").isPresent());
        assertTrue(second.getEntry("bar").isPresent());
        assertTrue(second.getEntry("baz").isPresent());
    }

    @Test
    void clear() {
    }
}
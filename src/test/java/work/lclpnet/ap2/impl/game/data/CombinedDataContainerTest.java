package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.game.data.DataEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombinedDataContainerTest {

    @Test
    void delete_fromAllChildren() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new ScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        second.addScore("foo", 5);
        second.addScore("bar", 7);

        first.add("foo");

        assertTrue(first.getEntry("foo").isPresent());
        assertTrue(second.getEntry("foo").isPresent());

        container.delete("foo");

        assertTrue(first.getEntry("foo").isEmpty());
        assertTrue(second.getEntry("foo").isEmpty());
    }

    @Test
    void getEntry_fromChildren() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new ScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        second.addScore("bar", 7);

        first.add("foo");

        assertTrue(container.getEntry("foo").isPresent());
        assertTrue(container.getEntry("bar").isPresent());
    }

    @Test
    void orderedEntries_inOrderOfChildrenAndWithoutDuplicates() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new ScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        second.addScore("foo", 5);
        second.addScore("bar", 7);

        first.add("foo");

        var order = container.orderedEntries()
                .map(DataEntry::subject)
                .map(StringRef::name)
                .toList();

        assertEquals(List.of("foo", "bar"), order);
    }

    @Test
    void freeze_childrenAreFrozen() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new ScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        container.freeze();

        second.addScore("foo", 5);
        second.addScore("bar", 7);

        first.add("foo");

        assertTrue(first.getEntry("foo").isEmpty());
        assertTrue(second.getEntry("bar").isEmpty());
    }

    @Test
    void ensureTracked_noDataAddedToFirst_thenAddedToLast() {
        var first = new OrderedDataContainer<>(StringRef::new);
        var second = new ScoreDataContainer<>(StringRef::new);
        var container = new CombinedDataContainer<>(List.of(first, second));

        container.ensureTracked("foo");
        container.ensureTracked("bar");
        container.ensureTracked("baz");

        assertTrue(first.getEntry("foo").isPresent());
        assertTrue(second.getEntry("bar").isPresent());
        assertTrue(second.getEntry("baz").isPresent());

        assertTrue(second.getEntry("foo").isEmpty());
        assertTrue(first.getEntry("bar").isEmpty());
        assertTrue(first.getEntry("baz").isEmpty());
    }

    @Test
    void clear() {
    }
}
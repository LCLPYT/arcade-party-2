package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.data.DataEntry;
import work.lclpnet.ap2.game.data.OrderedDataContainer;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class OrderedDataContainerTest {

    @Test
    void streamOrderedEntries() {
        var data = new OrderedDataContainer<>(StringRef::new);
        data.add("foo");
        data.add("bar");
        data.add("baz");

        assertEquals(List.of("foo", "bar", "baz"), data.streamOrderedEntries()
                .map(DataEntry::getSubject)
                .map(StringRef::name)
                .toList());
    }

    @Test
    void streamOrderedEntries_sameEntryInstance() {
        var data = new OrderedDataContainer<>(StringRef::new);
        data.add("foo");
        data.add("bar");
        data.add("baz");

        var foo = Objects.requireNonNull(data.getEntry("foo"));
        var bar = Objects.requireNonNull(data.getEntry("bar"));
        var baz = Objects.requireNonNull(data.getEntry("baz"));

        List<DataEntry<StringRef>> expectedList = List.of(foo, bar, baz);
        var actualList = data.streamOrderedEntries().toList();

        for (int i = 0, listSize = actualList.size(); i < listSize; i++) {
            var expected = expectedList.get(i);
            var actual = actualList.get(i);

            assertSame(expected, actual);
        }
    }
}

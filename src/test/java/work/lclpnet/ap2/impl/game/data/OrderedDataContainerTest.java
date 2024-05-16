package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.game.data.DataEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class OrderedDataContainerTest {

    @Test
    void orderedEntries() {
        var data = new OrderedDataContainer<>(StringRef::new);
        data.add("foo");
        data.add("bar");
        data.add("baz");

        assertEquals(List.of("foo", "bar", "baz"), data.orderedEntries()
                .map(DataEntry::subject)
                .map(StringRef::name)
                .toList());
    }

    @Test
    void orderedEntries_sameEntryInstance() {
        var data = new OrderedDataContainer<>(StringRef::new);
        data.add("foo");
        data.add("bar");
        data.add("baz");

        var foo = data.getEntry("foo");
        var bar = data.getEntry("bar");
        var baz = data.getEntry("baz");

        List<DataEntry<StringRef>> expectedList = List.of(foo, bar, baz);
        var actualList = data.orderedEntries().toList();

        for (int i = 0, listSize = actualList.size(); i < listSize; i++) {
            var expected = expectedList.get(i);
            var actual = actualList.get(i);

            assertSame(expected, actual);
        }
    }
}

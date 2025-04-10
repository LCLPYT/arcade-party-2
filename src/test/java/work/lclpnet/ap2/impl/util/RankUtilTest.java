package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static it.unimi.dsi.fastutil.objects.ObjectIntPair.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RankUtilTest {

    @Test
    void rank() {
        List<ObjectIntPair<String>> list = List.of(
                of("foo", 2),
                of("bar", 1),
                of("world", 2),
                of("hello", 6)
        );

        var ranked = RankUtil.rank(list::stream, ObjectIntPair::rightInt).toList();

        assertEquals(List.of(
                Set.of(of("bar", 1)),
                Set.of(of("foo", 2), of("world", 2)),
                Set.of(of("hello", 6))
        ), ranked);
    }
}
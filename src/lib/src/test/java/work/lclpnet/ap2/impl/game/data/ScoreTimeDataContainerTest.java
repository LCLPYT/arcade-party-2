package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.game.data.entry.IntScoreDataEntry;
import work.lclpnet.ap2.game.data.entry.ScoreTimeDataEntry;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ScoreTimeDataContainerTest {

    @Test
    void testEntrySameScore() {
        var container = new ScoreTimeDataContainer<>(StringRef::new);

        var playerA = "A";
        var playerB = "B";

        container.setScore(playerA, 5);
        container.setScore(playerB, 5);
        container.setScore(playerB, 6);
        container.setScore(playerA, 6);

        assertEquals(1, ((ScoreTimeDataEntry<StringRef>) Objects.requireNonNull(container.getEntry(playerB))).ranking());
        assertEquals(2, ((ScoreTimeDataEntry<StringRef>) Objects.requireNonNull(container.getEntry(playerA))).ranking());
    }

    @Test
    void testEntryDifferentScore() {
        var container = new ScoreTimeDataContainer<>(StringRef::new);

        var playerA = "A";
        var playerB = "B";

        container.setScore(playerA, 5);
        container.setScore(playerB, 6);

        assertInstanceOf(IntScoreDataEntry.class, Objects.requireNonNull(container.getEntry(playerA)));
        assertInstanceOf(IntScoreDataEntry.class, Objects.requireNonNull(container.getEntry(playerB)));
    }

    @Test
    void testOrder() {
        var container = new ScoreTimeDataContainer<>(StringRef::new);

        var playerA = "A";
        var playerB = "B";
        var playerC = "C";

        container.setScore(playerA, 5);
        container.setScore(playerC, 10);
        container.setScore(playerB, 10);

        var order = container.streamOrderedEntries().toList();
        assertEquals(playerC, order.getFirst().getSubject().name());
        assertEquals(playerB, order.get(1).getSubject().name());
        assertEquals(playerA, order.get(2).getSubject().name());

        assertInstanceOf(ScoreTimeDataEntry.class, order.getFirst());
        assertInstanceOf(ScoreTimeDataEntry.class, order.get(1));
        assertInstanceOf(IntScoreDataEntry.class, order.get(2));

        assertEquals(1, ((ScoreTimeDataEntry<StringRef>) order.getFirst()).ranking());
        assertEquals(2, ((ScoreTimeDataEntry<StringRef>) order.get(1)).ranking());
    }
}
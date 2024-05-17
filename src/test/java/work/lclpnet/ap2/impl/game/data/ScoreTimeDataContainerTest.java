package work.lclpnet.ap2.impl.game.data;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.game.data.entry.ScoreDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreTimeDataEntry;

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

        assertEquals(1, ((ScoreTimeDataEntry<StringRef>) container.getEntry(playerB)).ranking());
        assertEquals(2, ((ScoreTimeDataEntry<StringRef>) container.getEntry(playerA)).ranking());
    }

    @Test
    void testEntryDifferentScore() {
        var container = new ScoreTimeDataContainer<>(StringRef::new);

        var playerA = "A";
        var playerB = "B";

        container.setScore(playerA, 5);
        container.setScore(playerB, 6);

        assertInstanceOf(ScoreDataEntry.class, container.getEntry(playerA));
        assertInstanceOf(ScoreDataEntry.class, container.getEntry(playerB));
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

        var order = container.orderedEntries().toList();
        assertEquals(playerC, order.getFirst().subject().name());
        assertEquals(playerB, order.get(1).subject().name());
        assertEquals(playerA, order.get(2).subject().name());

        assertInstanceOf(ScoreTimeDataEntry.class, order.getFirst());
        assertInstanceOf(ScoreTimeDataEntry.class, order.get(1));
        assertInstanceOf(ScoreDataEntry.class, order.get(2));

        assertEquals(1, ((ScoreTimeDataEntry<StringRef>) order.getFirst()).ranking());
        assertEquals(2, ((ScoreTimeDataEntry<StringRef>) order.get(1)).ranking());
    }

    @Test
    void getBestSubject() {
        var container = new ScoreTimeDataContainer<>(StringRef::new);

        var playerA = "A";
        var playerB = "B";

        container.setScore(playerA, 5);
        container.setScore(playerB, 5);
        container.setScore(playerB, 6);
        container.setScore(playerA, 6);

        var bestSubject = container.getBestSubject(StringRef::name).orElseThrow();

        assertEquals("B", bestSubject);
    }
}
package work.lclpnet.ap2.impl.game.data;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreDataContainerTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void orderedEntries() {
        var container = new ScoreDataContainer<>(StringRef::new);

        container.setScore("Player_A", 12);
        container.setScore("Player_B", 5);
        container.setScore("Player_C", 15);

        var order = container.orderedEntries().toList();

        assertEquals(3, order.size());
        assertEquals("Player_C", order.get(0).subject().name());
        assertEquals("Player_A", order.get(1).subject().name());
        assertEquals("Player_B", order.get(2).subject().name());
    }
}
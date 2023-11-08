package work.lclpnet.ap2.impl.game.data;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.game.data.entry.ScoreDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreTimeDataEntry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreTimeDataContainerTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void testEntrySameScore() {
        var container = new ScoreTimeDataContainer();

        var playerA = player("A");
        var playerB = player("B");

        container.setScore(playerA, 5);
        container.setScore(playerB, 5);

        assertEquals(1, ((ScoreTimeDataEntry) container.getEntry(playerA)).ranking());
        assertEquals(2, ((ScoreTimeDataEntry) container.getEntry(playerB)).ranking());
    }

    @Test
    void testEntryDifferentScore() {
        var container = new ScoreTimeDataContainer();

        var playerA = player("A");
        var playerB = player("B");

        container.setScore(playerA, 5);
        container.setScore(playerB, 6);

        assertInstanceOf(ScoreDataEntry.class, container.getEntry(playerA));
        assertInstanceOf(ScoreDataEntry.class, container.getEntry(playerB));
    }

    @Test
    void testOrder() {
        var container = new ScoreTimeDataContainer();

        var playerA = player("A");
        var playerB = player("B");
        var playerC = player("C");

        container.setScore(playerA, 5);
        container.setScore(playerC, 10);
        container.setScore(playerB, 10);

        var order = container.orderedEntries().toList();
        assertEquals(playerC.getUuid(), order.get(0).getPlayer().uuid());
        assertEquals(playerB.getUuid(), order.get(1).getPlayer().uuid());
        assertEquals(playerA.getUuid(), order.get(2).getPlayer().uuid());

        assertInstanceOf(ScoreTimeDataEntry.class, order.get(0));
        assertInstanceOf(ScoreTimeDataEntry.class, order.get(1));
        assertInstanceOf(ScoreDataEntry.class, order.get(2));

        assertEquals(1, ((ScoreTimeDataEntry) order.get(0)).ranking());
        assertEquals(2, ((ScoreTimeDataEntry) order.get(1)).ranking());
    }

    private ServerPlayerEntity player(String name) {
        ServerPlayerEntity player = mock();

        when(player.getUuid())
                .thenReturn(UUID.randomUUID());

        when(player.getEntityName())
                .thenReturn(name);

        return player;
    }
}
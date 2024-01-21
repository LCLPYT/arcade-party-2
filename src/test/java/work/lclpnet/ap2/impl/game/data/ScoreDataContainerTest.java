package work.lclpnet.ap2.impl.game.data;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreDataContainerTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void orderedEntries() {
        var container = new ScoreDataContainer();

        container.setScore(player("Player_A"), 12);
        container.setScore(player("Player_B"), 5);
        container.setScore(player("Player_C"), 15);

        var order = container.orderedEntries().toList();

        assertEquals(3, order.size());
        assertEquals("Player_C", order.get(0).getPlayer().name());
        assertEquals("Player_A", order.get(1).getPlayer().name());
        assertEquals("Player_B", order.get(2).getPlayer().name());
    }

    private ServerPlayerEntity player(String name) {
        ServerPlayerEntity player = mock();

        when(player.getUuid())
                .thenReturn(UUID.randomUUID());

        when(player.getNameForScoreboard())
                .thenReturn(name);

        return player;
    }
}
package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChallengeResultTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void grantClosest3() {
        var res = new ChallengeResult();
        ServerPlayerEntity a = player(), b = player(), c = player(), d = player(), e = player();

        Map<ServerPlayerEntity, Integer> score = Map.of(a, 5, b, 10, c, 6, d, 1, e, 5);

        res.grantClosest3(score.keySet(), 7, player -> OptionalInt.of(score.get(player)));

        assertEquals(3, res.getPointsGained(c));
        assertEquals(2, res.getPointsGained(a));
        assertEquals(2, res.getPointsGained(e));
        assertEquals(1, res.getPointsGained(b));
        assertEquals(0, res.getPointsGained(d));
    }

    private static ServerPlayerEntity player() {
        ServerPlayerEntity player = mock();

        when(player.getUuid()).thenReturn(UUID.randomUUID());

        return player;
    }
}
package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChallengeResultTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void grantClosest3() {
        var res = new ChallengeResult();
        ServerPlayer a = player(), b = player(), c = player(), d = player(), e = player();

        Map<ServerPlayer, Integer> score = Map.of(a, 5, b, 10, c, 6, d, 1, e, 5);

        res.grantClosest3(score.keySet(), 7, score::get);

        assertEquals(3, res.getPointsGained(c));
        assertEquals(2, res.getPointsGained(a));
        assertEquals(2, res.getPointsGained(e));
        assertEquals(1, res.getPointsGained(b));
        assertEquals(0, res.getPointsGained(d));
    }

    private static ServerPlayer player() {
        ServerPlayer player = mock();

        when(player.getUUID()).thenReturn(UUID.randomUUID());

        return player;
    }
}
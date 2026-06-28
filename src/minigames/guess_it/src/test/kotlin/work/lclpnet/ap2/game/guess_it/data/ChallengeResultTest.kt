package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerPlayer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.*

internal class ChallengeResultTest {
    @Test
    fun grantClosest3() {
        val res = ChallengeResult()
        val a: ServerPlayer = player()
        val b: ServerPlayer = player()
        val c: ServerPlayer = player()
        val d: ServerPlayer = player()
        val e: ServerPlayer = player()

        val score = mapOf(
            a to 5,
            b to 10,
            c to 6,
            d to 1,
            e to 5
        )

        res.grantClosest3(score.keys, 7) { key: Any? -> score.get(key) }

        Assertions.assertEquals(3, res.getPointsGained(c))
        Assertions.assertEquals(2, res.getPointsGained(a))
        Assertions.assertEquals(2, res.getPointsGained(e))
        Assertions.assertEquals(1, res.getPointsGained(b))
        Assertions.assertEquals(0, res.getPointsGained(d))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }

        private fun player(): ServerPlayer {
            val player = Mockito.mock<ServerPlayer>()

            Mockito.`when`(player.getUUID()).thenReturn(UUID.randomUUID())

            return player
        }
    }
}
package work.lclpnet.ap2.game.glowing_bomb

import work.lclpnet.kibu.scheduler.Ticks
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * Tunes the fuse length and glowstone amount per lobby size to keep matches reasonably short.
 *
 * A player is eliminated once their anchor holds 4 glowstone, so the number of bombs that have to
 * explode on them ("rounds") follows from the amount carried per bomb:
 *  - amount 1 needs up to 4 rounds, amount 2 or 3 always needs exactly 2 rounds, amount 4 needs 1 round.
 *
 * Because 2 and 3 both eliminate in 2 rounds, an amount range of 2..3 has no round variance. Including 4
 * enables 1-round eliminations, while including 1 risks 4-round eliminations.
 *
 * Ignoring the short delays between bombs, the match length is roughly:
 *   (players - 1) * rounds * fuse
 *
 * Resulting best / worst case per tier:
 *   1..4 players:  amount 1..3, fuse 7..18s -> best 2 rounds, worst 4 rounds  (4p:  0:42 .. 3:36)
 *   5..6 players:  amount 2..3, fuse 6..14s -> always 2 rounds                (6p:  1:00 .. 2:20)
 *   7..8 players:  amount 2..4, fuse 5..12s -> best 1 round,  worst 2 rounds  (8p:  0:35 .. 2:48)
 *   9..12 players: amount 2..4, fuse 5..10s -> best 1 round,  worst 2 rounds  (12p: 0:55 .. 3:40)
 */
class GbConfig(
    val random: Random,
    val initialPlayerCount: Int,
) {

    fun randomFuseTicks(): Int {
        val minFuse = minFuseTicks()
        val maxFuse = maxFuseTicks()

        return random.nextInt(minFuse..maxFuse)
    }

    fun maxFuseTicks(): Int = when (initialPlayerCount) {
        in 1..4 -> Ticks.seconds(18)
        in 5..6 -> Ticks.seconds(14)  // avg 10s
        in 7..8 -> Ticks.seconds(12)
        else -> Ticks.seconds(10)  // avg 8.75s
    }

    fun minFuseTicks(): Int = when (initialPlayerCount) {
        in 1..4 -> Ticks.seconds(7)
        in 5..6 -> Ticks.seconds(6)
        else -> Ticks.seconds(5)
    }

    fun randomAmount(): Int = when (initialPlayerCount) {
        in 1..4 -> random.nextInt(1..3)
        in 5..6 -> random.nextInt(2..3)
        else -> random.nextInt(2..4)
    }
}
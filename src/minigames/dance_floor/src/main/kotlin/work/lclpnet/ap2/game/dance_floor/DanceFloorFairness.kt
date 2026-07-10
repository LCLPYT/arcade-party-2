package work.lclpnet.ap2.game.dance_floor

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private const val TICKS_PER_SECOND = 20f

/**
 * All tuning and derived math for keeping Dance Floor rounds fair.
 *
 * Each round the floor is painted with a color pattern.
 * One color becomes the safe color and the other blocks are removed after a reaction window.
 * Fairness means: from wherever a player stands, a block of the safe color is reachable before the blocks vanish.
 *
 * Given a round's base reaction [window][DanceFloorInstance.blockDelayTicks] (in ticks), this
 * produces the three fairness outputs:
 *  - [coverageCap]: the coarsest layout accepted this round. Ramps down as rounds speed up -> coarse patterns appear
 *  early and are filtered out once windows get short
 *  - [selectionReach]: how far a player can travel in the window, used to prefer a safe color that is reachable from everywhere.
 *  - [reactionTicks]: the actual window, extended just enough that the chosen color stays reachable even for
 *  a coarse fallback layout
 *
 * A layout's coarseness is its "coverage radius": the largest distance from any floor cell to the nearest cell of a color.
 * Lower means better distributed.
 */
class DanceFloorFairness(
    /** Conservative walk speed (blocks/second) used when preferring a well-distributed safe color. */
    val selectionSpeedBps: Float = 4.3f,
    /** Safety factor on [selectionSpeedBps]. */
    val selectionSafety: Float = 0.8f,
    /** Sprint speed (blocks/second) assumed by the survivability time-guarantee. */
    val guaranteeSpeedBps: Float = 5.6f,
    /**
     * Fraction of the worst-case gap the guarantee must cover.
     * Players usually start partway to a safe block rather than on the worst-case cell, so covering the full gap over-extends rounds.
     */
    val guaranteeReachFactor: Float = 0.5f,
    /** At/above this reaction window (ticks), the coarsest ([coarsestCoverage]) layouts are accepted. */
    val capWindowHigh: Int = 48,
    /** At/below this reaction window (ticks), only near-uniform ([finestCoverage]) layouts are accepted. */
    val capWindowLow: Int = 15,
    /** Coarsest coverage radius allowed early, at [capWindowHigh]. */
    val coarsestCoverage: Int = 26,
    /** Coarsest coverage radius allowed late, at [capWindowLow] and below (near-uniform floors). */
    val finestCoverage: Int = 2,
) {

    /** The coarsest layout coverage accepted for a round with the given reaction [window]. */
    fun coverageCap(window: Int): Int {
        val t = ((window - capWindowLow).toFloat() / (capWindowHigh - capWindowLow)).coerceIn(0f, 1f)

        return (finestCoverage + t * (coarsestCoverage - finestCoverage)).roundToInt()
    }

    /** How far (in blocks) a player can travel within the reaction [window]; used to pick the safe color. */
    fun selectionReach(window: Int): Int =
        (window / TICKS_PER_SECOND * selectionSpeedBps * selectionSafety).toInt()

    /**
     * The reaction [window] extended just enough to cross the chosen safe color's worst-case
     * [coverage] gap. Returns [window] unchanged whenever it is already sufficient.
     */
    fun reactionTicks(window: Int, coverage: Int): Int {
        val needed = ceil(coverage * guaranteeReachFactor / guaranteeSpeedBps * TICKS_PER_SECOND).toInt()

        return max(window, needed)
    }
}

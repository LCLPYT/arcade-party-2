package work.lclpnet.ap2.game.color

import net.minecraft.world.item.DyeColor
import java.util.*

/**
 * Assigns a [DyeColor] to each player based on ordered color preferences, drawn from a set of
 * available colors.
 *
 * The matching is a tier-based greedy: all 1st choices are resolved globally before any 2nd choice,
 * and so on. Contested colors within a tier are decided by a fair random draw. A preference that is
 * not among the available colors is skipped, so a player's lower priorities move up to fill the gap.
 * Players without a satisfiable preference receive a random leftover color.
 *
 * A single pass keeps colors distinct. When there are more players than available colors, the pass
 * is repeated independently for the players left over, until everyone has a color. Colors therefore
 * only repeat across passes, which keeps the number of players per color minimal while still giving
 * each leftover player another chance at their preferred color.
 */
object DyeColorAssigner {

    private const val TIERS = 3

    /**
     * Assign a color to each player, keeping the number of players per color minimal.
     * @param players The players to assign a color to.
     * @param preferences A function returning the ordered color preferences of a player.
     * @param random The source of randomness for tie-breaking and leftover assignment.
     * @param availableColors The colors that may be assigned. Defaults to all dye colors.
     * @return A map of player to the assigned color.
     */
    fun assign(
        players: List<UUID>,
        preferences: (UUID) -> List<DyeColor>,
        random: Random,
        availableColors: Set<DyeColor> = DyeColor.entries.toSet(),
    ): Map<UUID, DyeColor> {
        // fall back to the full palette if no colors were provided
        val palette = availableColors.ifEmpty { DyeColor.entries.toSet() }

        // drop preferences that aren't available; lower priorities move up as a result
        val prefs = players.associateWith { uuid -> preferences(uuid).filter { it in palette } }

        val result = HashMap<UUID, DyeColor>(players.size)
        var unassigned = players.toMutableList()

        // each pass assigns distinct colors; leftover players repeat the matching independently
        while (unassigned.isNotEmpty()) {
            unassigned = assignPass(unassigned, prefs, palette, random, result)
        }

        return result
    }

    /**
     * Run a single distinct-color matching pass, filling [result] and returning the players that
     * could not be assigned this pass (more players than colors).
     */
    private fun assignPass(
        players: List<UUID>,
        prefs: Map<UUID, List<DyeColor>>,
        palette: Set<DyeColor>,
        random: Random,
        result: MutableMap<UUID, DyeColor>,
    ): MutableList<UUID> {
        val used = EnumSet.noneOf(DyeColor::class.java)
        val unassigned = players.toMutableList()

        for (tier in 0 until TIERS) {
            if (unassigned.isEmpty()) break

            // group unassigned players by the still-available color they want at this tier
            val contenders = HashMap<DyeColor, MutableList<UUID>>()

            for (uuid in unassigned) {
                val color = prefs.getValue(uuid).getOrNull(tier) ?: continue
                if (color in used) continue
                contenders.getOrPut(color) { mutableListOf() }.add(uuid)
            }

            // resolve each contested color fairly; iterate colors in random order
            for ((color, wanters) in contenders.entries.shuffled(random)) {
                val winner = if (wanters.size == 1) wanters[0] else wanters.shuffled(random).first()
                result[winner] = color
                used.add(color)
                unassigned.remove(winner)
            }
        }

        // fill players without a satisfiable preference with distinct still-unused colors
        val remainingColors = palette.filter { it !in used }.shuffled(random).toMutableList()
        val leftover = mutableListOf<UUID>()

        for (uuid in unassigned) {
            if (remainingColors.isEmpty()) {
                leftover.add(uuid)  // no colors left this pass, try again in the next one
            } else {
                result[uuid] = remainingColors.removeAt(remainingColors.size - 1)
            }
        }

        return leftover
    }
}

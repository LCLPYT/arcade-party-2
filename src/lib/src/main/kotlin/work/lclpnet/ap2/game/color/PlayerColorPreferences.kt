package work.lclpnet.ap2.game.color

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.DyeColor
import java.util.*

/**
 * Stores each player's preferred [DyeColor]s in priority order (1st, 2nd, 3rd) and assigns a
 * distinct color to each player of a game via a preference-aware matching.
 *
 * Implementations only need to provide the storage ([get] / [set] / [clear]); the matching is
 * provided as a default and delegates to [DyeColorAssigner]. This keeps the interface as a seam for
 * a future persistent backend.
 */
interface PlayerColorPreferences {

    /**
     * Get the ordered color preferences of a player.
     * @param uuid The player uuid.
     * @return The preferred colors, most preferred first (0 to 3 entries).
     */
    fun get(uuid: UUID): List<DyeColor>

    /**
     * Set the ordered color preferences of a player.
     * @param uuid The player uuid.
     * @param colors The preferred colors, most preferred first.
     */
    fun set(uuid: UUID, colors: List<DyeColor>)

    /**
     * Remove the stored color preferences of a player.
     * @param uuid The player uuid.
     */
    fun clear(uuid: UUID)

    /**
     * Assign a distinct [DyeColor] to each of the given players, honoring their preferences as much
     * as possible.
     * @param players The players to assign colors to.
     * @param random The source of randomness used for tie-breaking and leftover assignment.
     * @param availableColors The colors that may be assigned. Defaults to all dye colors.
     * @return A map of player uuid to the assigned color.
     */
    fun assign(
        players: Iterable<ServerPlayer>,
        random: Random,
        availableColors: Set<DyeColor> = DyeColor.entries.toSet(),
    ): Map<UUID, DyeColor> =
        DyeColorAssigner.assign(players.map { it.uuid }, this::get, random, availableColors)
}

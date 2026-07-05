package work.lclpnet.ap2.game.color

import net.minecraft.world.item.DyeColor
import java.util.*

/**
 * An in-memory [PlayerColorPreferences] implementation whose preferences live for the duration of
 * the party session only. A persistent backend can replace this later without touching callers.
 */
class SessionColorPreferences : PlayerColorPreferences {

    private val preferences = HashMap<UUID, List<DyeColor>>()

    @Synchronized
    override fun get(uuid: UUID): List<DyeColor> = preferences[uuid] ?: emptyList()

    @Synchronized
    override fun set(uuid: UUID, colors: List<DyeColor>) {
        preferences[uuid] = colors.distinct().take(MAX_PREFERENCES)
    }

    @Synchronized
    override fun clear(uuid: UUID) {
        preferences.remove(uuid)
    }

    companion object {
        const val MAX_PREFERENCES = 3
    }
}

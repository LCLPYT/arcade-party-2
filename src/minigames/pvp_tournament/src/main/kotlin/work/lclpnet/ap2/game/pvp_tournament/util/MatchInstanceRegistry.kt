package work.lclpnet.ap2.game.pvp_tournament.util

import net.minecraft.world.entity.Avatar
import work.lclpnet.ap2.game.pvp_tournament.gen.Match

/**
 * Thread-safe registry of live [MatchInstance]s.
 *
 * Centralizes synchronization around the `match -> instance` map so callers do not
 * have to manage `synchronized` blocks themselves.
 */
class MatchInstanceRegistry {

    private val instances = mutableMapOf<Match, MatchInstance>()

    @Synchronized
    fun getOrCreate(match: Match, factory: (Match) -> MatchInstance): MatchInstance =
        instances.computeIfAbsent(match, factory)

    @Synchronized
    operator fun get(match: Match): MatchInstance? = instances[match]

    /**
     * Marks the instance for [match] as started, atomically.
     * Returns the instance if the transition happened, or null if the instance is missing
     * or was already started.
     */
    @Synchronized
    fun markStarted(match: Match): MatchInstance? {
        val inst = instances[match] ?: return null
        if (inst.started) return null
        inst.started = true
        return inst
    }

    /**
     * Removes the instance for [match] and returns it, or null if absent.
     */
    @Synchronized
    fun remove(match: Match): MatchInstance? {
        val inst = instances.remove(match) ?: return null
        inst.started = false
        return inst
    }

    /**
     * Snapshot of current values for entity lookup. Returned list is independent of the registry.
     */
    @Synchronized
    fun snapshot(): List<MatchInstance> = instances.values.toList()

    fun findByParticipant(entity: Avatar): MatchInstance? =
        snapshot().find { entity in it.participants }
}

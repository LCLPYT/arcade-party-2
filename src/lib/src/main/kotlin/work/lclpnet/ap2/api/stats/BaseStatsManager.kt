package work.lclpnet.ap2.api.stats

import net.minecraft.resources.Identifier
import work.lclpnet.ap2.api.game.GameInfo
import work.lclpnet.ap2.api.game.data.GenericGameResult
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapDescriptor

data class Stat<T>(val id: String, val default: T)

typealias StatSet = Set<Stat<*>>

class Stats(stats: StatSet) {

    private val stats: MutableMap<Stat<*>, Any?> = stats.associateWith { it.default }.toMutableMap()

    operator fun <T> set(stat: Stat<T>, value: T) {
        stats[stat] = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(stat: Stat<T>): T {
        return stats[stat] as T
    }

    fun entries(): Set<Map.Entry<Stat<*>, Any?>> {
        return stats.entries.toSet()
    }
}

interface StatsResult {
    val gameId: Identifier
    val mapId: MapDescriptor
    fun type(): String
}

interface StatsManager<Ref : SubjectRef> {
    fun freeze()
    fun getResult(gameInfo: GameInfo, map: GameMap, result: GenericGameResult<Ref>): StatsResult
}

open class BaseStatsManager<T, Ref : SubjectRef>(
    val stats: StatSet,
    val refs: SubjectRefFactory<T, Ref>
) {
    private val entries = mutableMapOf<Ref, Stats>()
    private var frozen = false

    @Synchronized
    fun <U> modify(subject: T, stat: Stat<U>, action: (U) -> U): U {
        if (frozen) return get(subject, stat)

        val stats = statsOf(subject)

        val value = action(stats[stat])

        stats[stat] = value

        return value
    }

    @Synchronized
    fun <U> set(subject: T, stat: Stat<U>, value: U): BaseStatsManager<T, Ref> {
        if (frozen) return this

        statsOf(subject)[stat] = value

        return this
    }

    @Synchronized
    fun <U> get(subject: T, stat: Stat<U>): U {
        return statsOf(subject)[stat]
    }

    @JvmOverloads
    fun increment(subject: T, stat: Stat<Int>, amount: Int = 1): Int =
        modify(subject, stat) { it + amount }

    private fun statsOf(subject: T): Stats {
        val stats = entries.computeIfAbsent(refs.create(subject)) { Stats(this@BaseStatsManager.stats) }

        return stats
    }

    fun freeze() {
        frozen = true
    }

    fun getEntries() = entries.toMap()
}
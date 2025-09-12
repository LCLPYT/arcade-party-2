package work.lclpnet.ap2.api.stats

import net.minecraft.server.network.ServerPlayerEntity
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.impl.game.data.type.PlayerRef

data class Stat<T>(val id: String, val default: T)

typealias StatSet = Set<Stat<*>>

class Stats(stats: StatSet) {

    val stats: MutableMap<Stat<*>, Any?> = stats.associateWith { it.default }.toMutableMap()

    fun <T> set(stat: Stat<T>, value: T) {
        stats[stat] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(stat: Stat<T>): T {
        return stats[stat] as T
    }
}

open class StatsManager<T, Ref : SubjectRef>(val stats: StatSet, val refs: SubjectRefFactory<T, Ref>) {

    private val entries = mutableMapOf<Ref, Stats>()

    @Synchronized
    fun <U> modify(subject: T, stat: Stat<U>, action: (U) -> U): StatsManager<T, Ref> {
        val stats = statsOf(subject)

        stats.set(stat, action(stats.get(stat)))

        return this
    }

    @Synchronized
    fun <U> set(subject: T, stat: Stat<U>, value: U): StatsManager<T, Ref> {
        statsOf(subject).set(stat, value)

        return this
    }

    @JvmOverloads
    fun increment(subject: T, stat: Stat<Int>, amount: Int = 1): StatsManager<T, Ref> =
        modify(subject, stat) { it + amount }

    private fun statsOf(subject: T): Stats {
        val stats = entries.computeIfAbsent(refs.create(subject)) { Stats(this@StatsManager.stats) }

        return stats
    }
}

class FFAStatsManager(stats: StatSet) : StatsManager<ServerPlayerEntity, PlayerRef>(stats, PlayerRef::create)
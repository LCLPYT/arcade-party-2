package work.lclpnet.ap2.api.stats

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import work.lclpnet.ap2.api.game.data.GenericGameResult
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.kibu.translate.text.TranslatedText

data class Stat<T>(
    val id: String,
    val default: T,
    val higherIsBetter: Boolean = true,
    val unit: StatUnit = StatUnits.Plain,
    val display: Boolean = true,
)

typealias StatSet = Set<Stat<out Any>>

class Stats(stats: StatSet) {

    private val stats: MutableMap<Stat<*>, Any?> = stats.associateWith { it.default }.toMutableMap()

    operator fun <T> set(stat: Stat<T>, value: T) {
        stats[stat] = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(stat: Stat<T>): T {
        require(stats.containsKey(stat)) {
            "Stat '${stat.id}' is not registered"
        }

        return stats[stat] as T
    }

    fun entries(): Set<Map.Entry<Stat<*>, Any?>> {
        return stats.entries.toSet()
    }
}

class StatsView<Ref : SubjectRef>(
    val stats: StatSet,
    val order: List<ObjectIntPair<Ref>>,
    val results: Map<Ref, Stats>,
    val details: Map<Ref, TranslatedText> = emptyMap(),
)

interface StatsResult {
    val summary: GameSummary
    val type: String
}

interface StatsManager<Ref : SubjectRef> {
    fun fillDefaults(result: GenericGameResult<Ref>)
    fun freeze()
    fun getResult(summary: GameSummary, result: GenericGameResult<Ref>, details: Map<Ref, TranslatedText>): StatsResult
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

    @Synchronized
    fun fillDefaults(refs: Iterable<Ref>) {
        if (frozen) return

        for (ref in refs) {
            entries.computeIfAbsent(ref) { Stats(stats) }
        }
    }

    fun freeze() {
        frozen = true
    }

    fun getEntries() = entries.toMap()
}
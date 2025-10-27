package work.lclpnet.ap2.api.stats

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import work.lclpnet.ap2.api.game.GameInfo
import work.lclpnet.ap2.api.game.data.GenericGameResult
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.lobby.game.map.GameMap
import work.lclpnet.lobby.game.map.MapDescriptor

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

open class BaseStatsManager<T, Ref : SubjectRef>(
    val stats: StatSet,
    val refs: SubjectRefFactory<T, Ref>
) {
    private val entries = mutableMapOf<Ref, Stats>()
    private var frozen = false

    @Synchronized
    fun <U> modify(subject: T, stat: Stat<U>, action: (U) -> U): BaseStatsManager<T, Ref> {
        if (frozen) return this

        val stats = statsOf(subject)

        stats[stat] = action(stats[stat])

        return this
    }

    @Synchronized
    fun <U> set(subject: T, stat: Stat<U>, value: U): BaseStatsManager<T, Ref> {
        if (frozen) return this

        statsOf(subject)[stat] = value

        return this
    }

    @JvmOverloads
    fun increment(subject: T, stat: Stat<Int>, amount: Int = 1): BaseStatsManager<T, Ref> =
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

interface StatsManager<Ref : SubjectRef> {
    fun freeze()
    fun getResult(gameInfo: GameInfo, map: GameMap, result: GenericGameResult<Ref>): StatsResult
}

class FFAStatsResult(
    override val gameId: Identifier,
    override val mapId: MapDescriptor,
    val order: List<ObjectIntPair<PlayerRef>>,
    val results: Map<PlayerRef, Stats>
) : StatsResult {
    override fun type() = "ffa"
}

class FFAStatsManager(stats: StatSet) : BaseStatsManager<ServerPlayerEntity, PlayerRef>(stats, PlayerRef::create), StatsManager<PlayerRef> {

    override fun getResult(gameInfo: GameInfo, map: GameMap, result: GenericGameResult<PlayerRef>): FFAStatsResult {
        val results = getEntries()

        return FFAStatsResult(gameInfo.id, map.descriptor, result.subjectResults, results)
    }
}
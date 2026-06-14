package work.lclpnet.ap2.game.data

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import work.lclpnet.ap2.api.game.data.*
import work.lclpnet.ap2.game.data.entry.IntScoreDataEntry
import java.util.*
import java.util.stream.IntStream
import java.util.stream.Stream

/**
 * A data container that orders the subjects by their integer score.
 * The scores can still be updated after a subject was added.
 * The subject with the highest score is the winner.
 */
class IntScoreDataContainer<T, Ref : SubjectRef> @JvmOverloads constructor(
    refs: SubjectRefFactory<T, Ref>,
    val ordering: Ordering = Ordering.DESCENDING,
    private val detailKey: String? = null
) : BaseDataContainer<T, Ref>(refs), IntDataContainer<T, Ref> {
    private val scoreMap = Object2IntOpenHashMap<Ref>()
    private val listeners = ArrayList<ScoreListener<T, Int>>()

    override fun setScore(subject: T, score: Int) {
        val ref = refs.create(subject)

        setScore(ref, score)

        for (listener in listeners) {
            listener.accept(subject, score)
        }
    }

    @Synchronized
    fun setScore(ref: Ref, score: Int) {
        scoreMap.put(ref, score)
    }

    override fun addScore(subject: T, add: Int) {
        val key = refs.create(subject)

        val score = addScore(key, add)

        for (listener in listeners) {
            listener.accept(subject, score)
        }
    }

    @Synchronized
    fun addScore(ref: Ref, add: Int): Int =
        scoreMap.compute(ref) { _, score: Int? -> (score ?: 0) + add }!!

    override fun getScore(subject: T): Int =
        getScore(refs.create(subject))

    @Synchronized
    fun getScore(ref: Ref): Int =
        scoreMap.computeIfAbsent(ref) { _ -> 0 }

    @Synchronized
    override fun getEntry(ref: Ref): DataEntry<Ref>? {
        if (!scoreMap.containsKey(ref)) {
            return null
        }

        val score = scoreMap.getInt(ref)

        return IntScoreDataEntry(ref, score, detailKey)
    }

    @Synchronized
    override fun streamOrderedEntries(): Stream<IntScoreDataEntry<Ref>> {
        return scoreMap.object2IntEntrySet().stream()
            .map { entry ->
                IntScoreDataEntry(
                    entry.key,
                    entry.intValue,
                    detailKey
                )
            }
            .sorted(ordering.orderInt { entry -> entry.score })
    }

    override fun add(subject: T) {
        identityIfAbsent(subject)
    }

    override fun identityIfAbsent(subject: T) {
        addScore(subject, 0)
    }

    @Synchronized
    override fun clear() {
        scoreMap.clear()
    }

    @get:Synchronized
    val bestScore: Optional<Int>
        get() = ordering.best(scores())

    @get:Synchronized
    val worstScore: Optional<Int>
        get() = ordering.opposite().best(scores())

    @Synchronized
    private fun scores(): IntStream =
        scoreMap.values.intStream()

    @Synchronized
    fun getBestSubjects(resolver: SubjectRefResolver<T, Ref>): Set<T> {
        val best = this.bestScore

        if (best.isEmpty) return emptySet()

        val bestScore = best.get()

        return scoreMap.keys
            .filter { ref: Ref -> scoreMap.getInt(ref) == bestScore }
            .mapNotNull { ref: Ref -> resolver.resolve(ref) }
            .toSet()
    }

    override fun register(listener: ScoreListener<T, Int>) {
        listeners.add(listener)
    }

    @Synchronized
    override fun copy(): DataContainer<T, Ref> {
        val copy = IntScoreDataContainer(refs, ordering, detailKey)

        copy.scoreMap.putAll(this.scoreMap)

        return copy
    }
}

package work.lclpnet.ap2.game.data

import it.unimi.dsi.fastutil.objects.Object2DoubleMap
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap
import work.lclpnet.ap2.api.game.data.*
import work.lclpnet.ap2.game.data.entry.DoubleScoreDataEntry
import java.util.*
import java.util.stream.DoubleStream
import java.util.stream.Stream
import kotlin.math.abs

/**
 * A data container that orders the subjects by their integer score.
 * The scores can still be updated after a subject was added.
 * The subject with the highest score is the winner.
 */
class DoubleScoreDataContainer<T, Ref : SubjectRef> @JvmOverloads constructor(
    refs: SubjectRefFactory<T, Ref>,
    val ordering: Ordering = Ordering.DESCENDING,
    private val detailKey: String? = null,
    private val format: String = "%.2f"
) : BaseDataContainer<T, Ref>(refs), DataContainer<T, Ref>, ScoreListenerView<T, Double> {

    private val scoreMap: Object2DoubleMap<Ref> = Object2DoubleOpenHashMap<Ref>()
    private val listeners = ArrayList<ScoreListener<T, Double>>()

    fun setScore(subject: T, score: Double) {
        val ref = refs.create(subject)

        setScore(ref, score)

        for (listener in listeners) {
            listener.accept(subject, score)
        }
    }

    @Synchronized
    fun setScore(ref: Ref, score: Double) {
        scoreMap.put(ref, score)
    }

    fun addScore(subject: T, add: Double) {
        val key = refs.create(subject)

        val score = addScore(key, add)

        for (listener in listeners) {
            listener.accept(subject, score)
        }
    }

    @Synchronized
    fun addScore(ref: Ref, add: Double): Double = scoreMap.computeDouble(ref) { _, score ->
        (score ?: 0.0) + add
    }

    fun getScore(subject: T): Double = getScore(refs.create(subject))

    @Synchronized
    fun getScore(ref: Ref): Double = scoreMap.computeIfAbsent(ref) { _ -> 0.0 }

    @Synchronized
    override fun getEntry(ref: Ref): DataEntry<Ref>? {
        val score = scoreMap.getOrDefault(ref, Double.NaN)

        if (score.isNaN()) {
            return null
        }

        return DoubleScoreDataEntry(ref, score, format, detailKey)
    }

    @Synchronized
    override fun streamOrderedEntries(): Stream<DoubleScoreDataEntry<Ref>> {
        return scoreMap.object2DoubleEntrySet().stream()
            .map { entry ->
                DoubleScoreDataEntry(
                    entry.key,
                    entry.doubleValue,
                    format,
                    detailKey
                )
            }
            .sorted(ordering.orderDouble { it.score })
    }

    override fun add(subject: T) {
        identityIfAbsent(subject)
    }

    override fun identityIfAbsent(subject: T) {
        addScore(subject, 0.0)
    }

    @Synchronized
    override fun clear() {
        scoreMap.clear()
    }

    @get:Synchronized
    val bestScore: Optional<Double>
        get() = ordering.best(scores())

    @get:Synchronized
    val worstScore: Optional<Double>
        get() = ordering.opposite().best(scores())

    @Synchronized
    private fun scores(): DoubleStream {
        return scoreMap.values.doubleStream()
    }

    @Synchronized
    fun getBestSubjects(resolver: SubjectRefResolver<T, Ref>): Set<T> {
        val best = this.bestScore

        if (best.isEmpty) return emptySet()

        val bestScore = best.get()

        return scoreMap.keys
            .filter { ref -> abs(scoreMap.getDouble(ref) - bestScore) < 1e-10 }
            .mapNotNull { ref -> resolver.resolve(ref) }
            .toSet()
    }

    @Synchronized
    override fun copy(): DataContainer<T, Ref> {
        val copy = DoubleScoreDataContainer(refs, ordering, detailKey)

        copy.scoreMap.putAll(this.scoreMap)

        return copy
    }

    override fun register(listener: ScoreListener<T, Double>) {
        listeners.add(listener)
    }

    override fun dispatchScoreEvents(subjects: Iterable<T>) {
        for (subject in subjects) {
            identityIfAbsent(subject)
        }
    }
}

package work.lclpnet.ap2.game.data

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.game.data.entry.IntScoreDataEntry
import work.lclpnet.ap2.game.data.entry.ScoreTimeDataEntry
import work.lclpnet.ap2.game.data.entry.ScoreView
import java.util.function.ToIntFunction
import java.util.stream.Stream

/**
 * A score container that orders the subjects by their integer score, like [IntScoreDataContainer],
 * but the order in which the scores who reached is saved.
 * In case two subjects have the same score, the subject who reached the score first is ranked higher.
 */
class ScoreTimeDataContainer<T, Ref : SubjectRef> @JvmOverloads constructor(
    refs: SubjectRefFactory<T, Ref>,
    private val detailKey: String? = null
) : BaseDataContainer<T, Ref>(refs), IntDataContainer<T, Ref> {

    private val score = Object2IntOpenHashMap<Ref>()
    private val lastTransaction = Object2LongOpenHashMap<Ref>()
    private val listeners = ArrayList<ScoreListener<T, Int>>()

    /** An incrementing transaction counter. Used to determine who got to which score first.  */
    private var transaction: Long = 0

    override fun setScore(subject: T, score: Int) {
        synchronized(this) {
            val ref = refs.create(subject)
            this.score.put(ref, score)
            modified(ref)
        }

        for (listener in listeners) {
            listener.accept(subject, score)
        }
    }

    override fun addScore(subject: T, add: Int) {
        val score: Int

        synchronized(this) {
            val ref = refs.create(subject)
            score = this.score.compute(ref) { _, prev: Int? -> (prev ?: 0) + add }!!
            modified(ref)
        }

        for (listener in listeners) {
            listener.accept(subject, score)
        }
    }

    @Synchronized
    override fun getScore(subject: T): Int {
        return score.getOrDefault(refs.create(subject), 0)
    }

    private fun modified(ref: Ref) {
        lastTransaction.put(ref, transaction++)
    }

    @Synchronized
    override fun getEntry(ref: Ref): DataEntry<Ref> {
        val score = this.score.getOrDefault(ref, 0)
        val ranking = getTimedRanking(ref)

        if (ranking == 0) {
            return IntScoreDataEntry(ref, score, detailKey)
        }

        return ScoreTimeDataEntry(ref, score, detailKey, ranking)
    }

    @Synchronized
    override fun streamOrderedEntries(): Stream<out DataEntry<Ref>> {
        return score.object2IntEntrySet().stream()
            .map { entry ->
                val ref = entry.key
                val ranking = getTimedRanking(ref)

                if (ranking == 0) {
                    IntScoreDataEntry(ref, entry.intValue, detailKey)
                } else {
                    ScoreTimeDataEntry(ref, entry.intValue, detailKey, ranking)
                }
            }
            .sorted(
                Comparator.comparingInt { obj: ScoreView -> obj.score }
                    .reversed()
                    .thenComparingInt(ToIntFunction { x: ScoreView ->
                        if (x is ScoreTimeDataEntry<*>) {
                            x.ranking
                        } else 0
                    })
            )
    }

    /**
     * Get the ranking among subjects with the same score.
     * 
     * @param ref The subject reference.
     * @return The ranking, or 0 if the score is unique.
     */
    @Synchronized
    private fun getTimedRanking(ref: Ref): Int {
        val subjectScore = score.getInt(ref)

        val ordered = score.object2IntEntrySet().stream()
            .filter { e -> e.intValue == subjectScore }
            .map { it.key }
            .sorted(Comparator.comparingLong { key -> lastTransaction.getLong(key) })
            .toList()

        if (ordered.size <= 1) {
            return 0
        }

        var rank = 0

        for (r in ordered) {
            rank++

            if (ref == r) break
        }

        return rank
    }

    override fun add(subject: T) {
        identityIfAbsent(subject)
    }

    override fun identityIfAbsent(subject: T) {
        addScore(subject, 0)
    }

    @Synchronized
    override fun clear() {
        score.clear()
        lastTransaction.clear()
    }

    override fun register(listener: ScoreListener<T, Int>) {
        listeners.add(listener)
    }

    override fun dispatchScoreEvents(subjects: Iterable<T>) {
        for (subject in subjects) {
            identityIfAbsent(subject)
        }
    }

    @Synchronized
    override fun copy(): DataContainer<T, Ref> {
        val copy = ScoreTimeDataContainer(refs)
        copy.transaction = transaction
        copy.score.putAll(this.score)
        copy.lastTransaction.putAll(this.lastTransaction)

        return copy
    }
}

package work.lclpnet.ap2.game.data

import com.google.common.collect.AbstractIterator
import work.lclpnet.ap2.impl.util.RankUtil
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport

interface DataContainer<T, Ref : SubjectRef> {

    fun getEntry(subject: T): DataEntry<Ref>?

    fun getEntry(ref: Ref): DataEntry<Ref>?

    fun streamOrderedEntries(): Stream<out DataEntry<Ref>>

    /**
     * Adds a player to the container.
     * Can be used to add the winner for data containers that track order.
     * In the case of score containers, this is probably equivalent to calling [identityIfAbsent].
     * For other containers, e.g. the EliminationDataContainer, this is different though, as it modifies the order.
     * @param subject The subject.
     */
    fun add(subject: T)

    /**
     * Sets the identity value for the given subject, if it doesn't exist in the container yet.
     * This could mean that the subject is tracked with a zero for score containers.
     * For some containers, e.g. the EliminationDataContainer, this may be a noop, as they need to track the order.
     * @param subject The subject.
     */
    fun identityIfAbsent(subject: T)

    fun clear()

    fun copy(): DataContainer<T, Ref>

    val isEmpty: Boolean
        get() = streamEntriesRanked().findAny().isEmpty

    fun streamEntriesRanked(): Stream<Set<Pair<Ref, Int>>> = RankUtil.rank(
        { streamRankedEntries() },
        { (_, rank) -> rank }
    )

    fun streamRankedEntries(): Stream<Pair<Ref, Int>> {
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                this.rankedEntries, 0
            ), false
        )
    }

    val rankedEntries: Iterator<Pair<Ref, Int>>
        get() {
            val parent = streamOrderedEntries().iterator()

            return object : AbstractIterator<Pair<Ref, Int>>() {
                var rank: Int = 1
                var skippedRanks: Int = 0
                var prevEntry: DataEntry<Ref>? = null

                override fun computeNext(): Pair<Ref, Int>? {
                    if (!parent.hasNext()) {
                        endOfData()
                        return null
                    }

                    val dataEntry = parent.next()
                    val prevEntry = this.prevEntry

                    if (prevEntry != null) {
                        if (prevEntry.scoreEquals(dataEntry)) {
                            skippedRanks++
                        } else {
                            rank += skippedRanks + 1
                            skippedRanks = 0
                        }
                    }

                    this.prevEntry = dataEntry

                    return dataEntry.subject to rank
                }
            }
        }
}

package work.lclpnet.ap2.game.data

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.game.data.entry.SimpleOrderDataEntry
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.stream.IntStream
import java.util.stream.Stream

/**
 * A data container for last one standing game modes.
 * The last added subject is the winner.
 */
class EliminationDataContainer<T, Ref : SubjectRef>(
    refs: SubjectRefFactory<T, Ref>
) : BaseDataContainer<T, Ref>(refs) {

    private val index = Object2IntArrayMap<Ref>()
    private val order = ArrayList<MutableMap<Ref, SimpleOrderDataEntry<Ref>>>()

    override fun add(subject: T) {
        add(subject, null)
    }

    fun add(subject: T, data: TranslatedText?) {
        addAll(listOf(subject), data)
    }

    fun addAll(subjects: Iterable<T>) {
        addAll(subjects, null)
    }

    @Synchronized
    fun addAll(subjects: Iterable<T>, data: TranslatedText?) {
        val rank = order.size
        val mapping = HashMap<Ref, SimpleOrderDataEntry<Ref>>()

        for (subject in subjects) {
            val ref = refs.create(subject)

            if (index.containsKey(ref)) continue

            index.put(ref, rank)
            mapping[ref] = SimpleOrderDataEntry(ref, rank, data)
        }

        if (mapping.isEmpty()) return

        order.add(mapping)
    }

    @Synchronized
    override fun getEntry(ref: Ref): DataEntry<Ref>? {
        val rank = getRank(ref)

        if (rank < 0 || rank >= order.size) {
            return null
        }

        val mapping = order[rank]
        val dataEntry = mapping[ref]

        return dataEntry
    }

    @Synchronized
    override fun streamOrderedEntries(): Stream<DataEntry<Ref>> {
        // order needs to be reversed
        return IntStream.range(0, order.size)
            .mapToObj { i -> order[order.size - i - 1] }
            .flatMap { mapping -> mapping.values.stream() }
    }

    override fun identityIfAbsent(subject: T) {}

    @Synchronized
    override fun clear() {
        index.clear()
        order.clear()
    }

    private fun getRank(ref: Ref): Int {
        return index.getOrDefault(ref, -1)
    }

    @Synchronized
    override fun copy(): DataContainer<T, Ref> {
        val copy = EliminationDataContainer(refs)

        copy.index.putAll(this.index)
        copy.order.addAll(this.order)

        return copy
    }
}

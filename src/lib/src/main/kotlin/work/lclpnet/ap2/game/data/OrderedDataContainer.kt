package work.lclpnet.ap2.game.data

import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.game.data.entry.SimpleDataEntry
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.function.Function
import java.util.stream.Stream

/**
 * A data container that keeps the order the subjects were added.
 * The first added subject is the winner.
 */
class OrderedDataContainer<T, Ref : SubjectRef>(
    refs: SubjectRefFactory<T, Ref>
) : BaseDataContainer<T, Ref>(refs) {

    private val order = HashMap<Ref, Entry<Ref>>()

    override fun add(subject: T) {
        add(subject) {
            SimpleDataEntry(it)
        }
    }

    fun add(subject: T, data: TranslatedText?) {
        add(subject) {
            SimpleDataEntry(it, data)
        }
    }

    @Synchronized
    private fun add(subject: T, entryFactory: Function<Ref, SimpleDataEntry<Ref>>) {
        val ref = refs.create(subject)

        if (order.containsKey(ref)) return

        val dataEntry = entryFactory.apply(ref)

        order[ref] = Entry(order.size, dataEntry)
    }

    @Synchronized
    override fun getEntry(ref: Ref): DataEntry<Ref>? {
        val entry = order[ref] ?: return null

        return entry.dataEntry
    }

    @Synchronized
    override fun streamOrderedEntries(): Stream<out DataEntry<Ref>> {
        return order.values.stream()
            .sorted(Comparator.comparingInt { it.order })
            .map { it.dataEntry }
    }

    override fun identityIfAbsent(subject: T) {}

    @Synchronized
    override fun clear() {
        order.clear()
    }

    @Synchronized
    override fun copy(): DataContainer<T, Ref> {
        val copy = OrderedDataContainer(refs)

        copy.order.putAll(this.order)

        return copy
    }

    @JvmRecord
    private data class Entry<Ref : SubjectRef>(val order: Int, val dataEntry: SimpleDataEntry<Ref>)
}

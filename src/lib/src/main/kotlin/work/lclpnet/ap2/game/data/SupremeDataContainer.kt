package work.lclpnet.ap2.game.data

import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.game.data.entry.SupremeDataEntry
import java.util.stream.Stream

class SupremeDataContainer<T, Ref : SubjectRef>(
    refs: SubjectRefFactory<T, Ref>
) : BaseDataContainer<T, Ref>(refs) {
    private val entries = HashSet<Ref>()

    @Synchronized
    override fun add(subject: T) {
        entries.add(refs.create(subject))
    }

    @Synchronized
    override fun getEntry(ref: Ref): DataEntry<Ref>? {
        if (entries.contains(ref)) {
            return SupremeDataEntry(ref)
        }

        return null
    }

    @Synchronized
    override fun streamOrderedEntries(): Stream<out DataEntry<Ref>> {
        return entries.stream().map { subject -> SupremeDataEntry(subject) }
    }

    override fun identityIfAbsent(subject: T) {}

    @Synchronized
    override fun clear() {
        entries.clear()
    }

    @Synchronized
    override fun copy(): DataContainer<T, Ref> {
        val copy = SupremeDataContainer(refs)

        copy.entries.addAll(this.entries)

        return copy
    }
}

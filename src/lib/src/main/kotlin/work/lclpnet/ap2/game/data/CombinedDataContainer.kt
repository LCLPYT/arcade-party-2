package work.lclpnet.ap2.game.data

import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import java.util.stream.Stream

class CombinedDataContainer<T, Ref : SubjectRef>(
    children: List<DataContainer<T, Ref>>
) : DataContainer<T, Ref> {
    private val children: List<DataContainer<T, Ref>> = children.toList().also {
        require(children.isNotEmpty()) { "There needs to be at least on child data container" }
    }

    @Synchronized
    override fun getEntry(subject: T): DataEntry<Ref>? {
        for (child in children) {
            val entry = child.getEntry(subject)

            if (entry != null) {
                return entry
            }
        }

        return null
    }

    @Synchronized
    override fun getEntry(ref: Ref): DataEntry<Ref>? {
        for (child in children) {
            val entry = child.getEntry(ref)

            if (entry != null) {
                return entry
            }
        }

        return null
    }

    @Synchronized
    override fun streamOrderedEntries(): Stream<out DataEntry<Ref>> {
        val seen = mutableSetOf<Ref>()

        return children.stream()
            .flatMap { obj -> obj.streamOrderedEntries() }
            .filter { entry -> seen.add(entry.subject) }  // each entry only once. this is stateful, only sequential streams will work
    }

    override fun add(subject: T) {
        children.first().add(subject)
    }

    override fun identityIfAbsent(subject: T) {
        children.last().identityIfAbsent(subject)
    }

    @Synchronized
    override fun clear() {
        for (child in children) {
            child.clear()
        }
    }

    @Synchronized
    override fun copy(): DataContainer<T, Ref> = CombinedDataContainer(
        children.map { it.copy() }.toList()
    )
}

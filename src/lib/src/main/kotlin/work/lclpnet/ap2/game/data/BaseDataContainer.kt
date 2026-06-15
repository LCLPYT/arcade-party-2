package work.lclpnet.ap2.game.data

import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.ap2.api.game.data.SubjectRefFactory

abstract class BaseDataContainer<T, Ref : SubjectRef>(
    protected val refs: SubjectRefFactory<T, Ref>
) : DataContainer<T, Ref> {

    override fun getEntry(subject: T): DataEntry<Ref>? = getEntry(refs.create(subject))
}

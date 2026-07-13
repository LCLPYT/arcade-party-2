package work.lclpnet.ap2.game.data

abstract class BaseDataContainer<T, Ref : SubjectRef>(
    protected val refs: SubjectRefFactory<T, Ref>
) : DataContainer<T, Ref> {

    override fun getEntry(subject: T): DataEntry<Ref>? = getEntry(refs.create(subject))
}

package work.lclpnet.ap2.game.data

fun interface GameWinnersFactory<T, Ref : SubjectRef> {

    fun create(data: DataContainer<T, Ref>): GenericGameResult<Ref>
}

package work.lclpnet.ap2.api.game.data

fun interface SubjectRefResolver<T, Ref : SubjectRef> {
    fun resolve(ref: Ref): T?
}

package work.lclpnet.ap2.api.game.data

fun interface SubjectRefFactory<T, Ref> {
    fun create(subject: T): Ref
}

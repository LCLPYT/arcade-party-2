package work.lclpnet.ap2.game.data

fun interface SubjectRefFactory<T, Ref> {
    fun create(subject: T): Ref
}

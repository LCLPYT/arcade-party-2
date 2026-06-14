package work.lclpnet.ap2.game.data

fun interface IntScoreEventSource<Type> {
    fun register(listener: IntScoreEvent<Type>)
}

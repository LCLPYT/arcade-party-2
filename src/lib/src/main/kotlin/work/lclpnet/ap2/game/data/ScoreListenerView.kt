package work.lclpnet.ap2.game.data

fun interface ScoreListenerView<Type, Value> {
    fun register(listener: ScoreListener<Type, Value>)
}

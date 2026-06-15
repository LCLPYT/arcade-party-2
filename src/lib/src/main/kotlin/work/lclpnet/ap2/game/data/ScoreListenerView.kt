package work.lclpnet.ap2.game.data

interface ScoreListenerView<Type, Value> {
    fun register(listener: ScoreListener<Type, Value>)
    fun dispatchScoreEvents(subjects: Iterable<Type>)
}

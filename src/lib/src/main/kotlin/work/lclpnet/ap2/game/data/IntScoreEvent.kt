package work.lclpnet.ap2.game.data

fun interface IntScoreEvent<Type> {
    fun accept(player: Type, score: Int)
}

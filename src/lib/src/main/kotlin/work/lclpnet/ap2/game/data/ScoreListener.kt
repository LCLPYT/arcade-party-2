package work.lclpnet.ap2.game.data

fun interface ScoreListener<Subject, Value> {
    fun accept(subject: Subject, score: Value)
}

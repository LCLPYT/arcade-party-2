package work.lclpnet.ap2.api.game.sink

interface IntDataSink<T> {
    fun setScore(subject: T, score: Int)

    fun addScore(subject: T, add: Int)

    fun getScore(subject: T): Int
}

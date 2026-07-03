package work.lclpnet.ap2.game.data

interface IntDataSink<T> {
    fun setScore(subject: T, score: Int)

    fun addScore(subject: T, add: Int)

    fun getScore(subject: T): Int
}
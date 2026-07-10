package work.lclpnet.ap2.util.scoreboard

class ScoreboardLayout {
    private var topId = Int.MAX_VALUE - 1
    private var bottomId = Int.MIN_VALUE + 1

    fun addTop(): Int = topId--

    fun addBottom(): Int = bottomId++

    fun resolvePosition(position: Int): Int = when (position) {
        TOP -> addTop()
        BOTTOM -> addBottom()
        else -> 0
    }

    companion object {
        const val TOP: Int = 0
        const val BOTTOM: Int = 1
    }
}

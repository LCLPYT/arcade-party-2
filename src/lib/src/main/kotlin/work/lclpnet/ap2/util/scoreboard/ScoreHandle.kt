package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat

class ScoreHandle(
    val holder: String,
    val objective: CustomScoreboardObjective
) {

    fun setScore(score: Int) {
        objective.setScore(holder, score)
    }

    fun setDisplay(text: Component?) {
        objective.setDisplayName(holder, text)
    }

    fun setNumberFormat(numberFormat: NumberFormat?) {
        objective.setNumberFormat(holder, numberFormat)
    }
}

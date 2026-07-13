package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.server.level.ServerPlayer

class DynamicScoreHandle(
    val holder: String,
    val objective: DynamicScoreboardObjective
) {

    fun setScore(player: ServerPlayer, score: Int) {
        objective.setScore(player, holder, score)
    }

    fun setDisplay(player: ServerPlayer, text: Component?) {
        objective.setDisplayName(player, holder, text)
    }

    fun setNumberFormat(player: ServerPlayer, numberFormat: NumberFormat?) {
        objective.setNumberFormat(player, holder, numberFormat)
    }
}

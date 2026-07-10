package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.server.level.ServerPlayer

interface CustomScoreboardObjective {

    fun setScore(scoreHolder: String, score: Int)

    fun setDisplayName(scoreHolder: String, display: Component?)

    fun setNumberFormat(scoreHolder: String, numberFormat: NumberFormat?)

    fun removeEntry(scoreHolder: String)

    fun setScore(player: ServerPlayer, score: Int) {
        setScore(player.scoreboardName, score)
    }
}

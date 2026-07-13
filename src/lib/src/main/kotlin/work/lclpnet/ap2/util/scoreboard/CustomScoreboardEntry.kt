package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat

data class CustomScoreboardEntry(
    var display: Component?,
    var numberFormat: NumberFormat?,
    var score: Int
)

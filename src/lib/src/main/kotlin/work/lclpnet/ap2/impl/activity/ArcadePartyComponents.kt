package work.lclpnet.ap2.impl.activity

import work.lclpnet.activity.component.ComponentKey

object ArcadePartyComponents {
    val SCORE_BOARD: ComponentKey<ScoreboardComponent> = ComponentKey { context ->
        val server = context.server
        ScoreboardComponent(server.scoreboard, server.playerList)
    }
}

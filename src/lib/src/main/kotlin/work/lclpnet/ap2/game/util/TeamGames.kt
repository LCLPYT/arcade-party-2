package work.lclpnet.ap2.game.util

import work.lclpnet.ap2.api.game.team.TeamConfig
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.team.SimpleTeamManager

fun MiniGameHandle.createTeamManager(): TeamManager {
    val teamConfig = teamConfig.orElseGet {
        TeamConfig.defaultConfig()
    }

    val teamManager = SimpleTeamManager(server.playerList, teamConfig, scoreboardManager, playerUtil)

    teamManager.init(hooks)

    return teamManager
}
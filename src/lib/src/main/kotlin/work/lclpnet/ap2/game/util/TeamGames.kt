package work.lclpnet.ap2.game.util

import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.team.SimpleTeamManager
import work.lclpnet.ap2.game.team.TeamConfig
import work.lclpnet.ap2.game.team.TeamManager

fun MiniGameHandle.createTeamManager(): TeamManager {
    val teamConfig = teamConfig.orElseGet {
        TeamConfig.DEFAULT_CONFIG
    }

    val teamManager = SimpleTeamManager(server.playerList, teamConfig, scoreboardManager, playerUtil)

    teamManager.init(hooks)

    return teamManager
}
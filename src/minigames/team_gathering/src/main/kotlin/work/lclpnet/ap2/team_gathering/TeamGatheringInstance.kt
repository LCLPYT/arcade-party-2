package work.lclpnet.ap2.team_gathering

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.util.useDataContainer
import work.lclpnet.ap2.game.util.useTeamWinManager
import work.lclpnet.game.util.ResetWorldModifier

class TeamGatheringInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val teamManager: TeamManager,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = useDataContainer(teamManager, ::IntScoreDataContainer)
    override val winManager = useTeamWinManager(teamManager, map = null) { data }
    override val participantListener = null

    override fun start() {
    }
}
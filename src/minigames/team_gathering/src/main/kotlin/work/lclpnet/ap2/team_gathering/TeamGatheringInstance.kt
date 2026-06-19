package work.lclpnet.ap2.team_gathering

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.game.util.*
import work.lclpnet.game.util.ResetWorldModifier
import kotlin.math.ceil

class TeamGatheringInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val teamManager: TeamManager,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = useDataContainer(teamManager, ::IntScoreDataContainer)
    override val winManager = useTeamWinManager(teamManager, map = null) { data }
    override val participantListener = null

    init {
        useSurvivalMode()
    }

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }

        val teamCount = ceil(players().count() / 2f).toInt()
        val teamKeys = DyeTeamKey.entries.toSet().shuffled().take(teamCount)

        teamManager.partitionIntoTeams(players(), teamKeys)

        useStartup(::go)
    }

    private fun go() {
        walls.undo()
    }
}
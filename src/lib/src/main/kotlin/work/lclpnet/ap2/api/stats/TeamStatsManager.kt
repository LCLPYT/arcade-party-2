package work.lclpnet.ap2.api.stats

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.GameInfo
import work.lclpnet.ap2.api.game.data.GenericGameResult
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.game.data.type.TeamRef
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapDescriptor

class TeamStatsManager(
    teamStats: StatSet,
    playerStats: StatSet,
    teamRefs: SubjectRefFactory<Team, TeamRef>,
) : StatsManager<TeamRef> {

    val teams: BaseStatsManager<Team, TeamRef> = BaseStatsManager(teamStats, teamRefs)
    val players: BaseStatsManager<ServerPlayer, PlayerRef> = BaseStatsManager(playerStats, PlayerRef::create)

    override fun freeze() {
        teams.freeze()
        players.freeze()
    }

    override fun getResult(gameInfo: GameInfo, map: GameMap, result: GenericGameResult<TeamRef>): TeamStatsResult {
        val teamView = StatsView(teams.stats, result.subjectResults, teams.getEntries())
        val playerView = StatsView(players.stats, result.playerResults, players.getEntries())

        return TeamStatsResult(
            gameId = gameInfo.id,
            mapId = map.descriptor,
            teamView = teamView,
            playerView = playerView,
        )
    }
}

class TeamStatsResult(
    override val gameId: Identifier,
    override val mapId: MapDescriptor,
    val teamView: StatsView<TeamRef>,
    val playerView: StatsView<PlayerRef>,
) : StatsResult {
    override val type = "team"
}

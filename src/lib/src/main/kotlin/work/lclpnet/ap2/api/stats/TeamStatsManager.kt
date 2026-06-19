package work.lclpnet.ap2.api.stats

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.data.GenericGameResult
import work.lclpnet.ap2.api.game.data.SubjectRefFactory
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.data.type.TeamGameResult
import work.lclpnet.ap2.game.data.type.TeamRef
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.kibu.translate.text.TranslatedText

class TeamStatsManager(
    teamStats: StatSet,
    playerStats: StatSet,
    teamRefs: SubjectRefFactory<Team, TeamRef>,
) : StatsManager<TeamRef> {

    val teams: BaseStatsManager<Team, TeamRef> = BaseStatsManager(teamStats, teamRefs)
    val players: BaseStatsManager<ServerPlayer, PlayerRef> = BaseStatsManager(playerStats, PlayerRef::create)

    override fun fillDefaults(result: GenericGameResult<TeamRef>) {
        teams.fillDefaults(result.subjectResults.mapNotNull { it.left() })
        players.fillDefaults(result.playerResults.mapNotNull { it.left() })
    }

    override fun freeze() {
        teams.freeze()
        players.freeze()
    }

    override fun getResult(
        summary: GameSummary,
        result: GenericGameResult<TeamRef>,
        details: Map<TeamRef, TranslatedText>
    ): TeamStatsResult {
        val teamView = StatsView(teams.stats, result.subjectResults, teams.getEntries(), details)
        val playerView = StatsView(players.stats, result.playerResults, players.getEntries())
        val playerTeams = (result as? TeamGameResult)?.playerTeams ?: emptyMap()

        return TeamStatsResult(
            summary = summary,
            teamView = teamView,
            playerView = playerView,
            playerTeams = playerTeams,
        )
    }
}

class TeamStatsResult(
    override val summary: GameSummary,
    val teamView: StatsView<TeamRef>,
    val playerView: StatsView<PlayerRef>,
    val playerTeams: Map<PlayerRef, TeamRef>,
) : StatsResult {
    override val type = "team"
}

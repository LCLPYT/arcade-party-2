package work.lclpnet.ap2.game.data.type

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.GenericGameResult
import work.lclpnet.ap2.api.game.data.SubjectRefResolver
import work.lclpnet.ap2.game.team.Team
import java.util.*

class TeamGameResult(
    data: DataContainer<Team, TeamRef>,
    refResolver: SubjectRefResolver<Team, TeamRef>
) : GenericGameResult<TeamRef> {
    override val playerResults: List<ObjectIntPair<PlayerRef>>
    override val subjectResults: List<ObjectIntPair<TeamRef>>
    val playerTeams: MutableMap<PlayerRef, TeamRef>
    override val winningPlayers: Set<PlayerRef>
    override val winningSubjects: Set<TeamRef>

    init {
        val byRank: List<Set<ObjectIntPair<TeamRef>>> = data.streamEntriesRanked().toList()

        this.subjectResults = byRank.stream()
            .flatMap { it.stream() }
            .toList()

        val playerResults = ArrayList<ObjectIntPair<PlayerRef>>()
        val playerTeams = LinkedHashMap<PlayerRef, TeamRef>()

        for (teamRank in this.subjectResults) {
            val teamRef: TeamRef = teamRank.key()
            val team = refResolver.resolve(teamRef) ?: continue

            for (player in team.players) {
                val ref = PlayerRef.create(player)
                playerResults.add(ObjectIntPair.of(ref, teamRank.rightInt()))
                playerTeams[ref] = teamRef
            }
        }

        this.playerResults = playerResults.toList()
        this.playerTeams = Collections.unmodifiableMap(playerTeams)

        this.winningSubjects = when {
            byRank.isEmpty() -> emptySet()
            else -> byRank.first()
                .map { it.left() }
                .toSet()
        }

        this.winningPlayers = winningSubjects
            .mapNotNull { ref -> refResolver.resolve(ref) }
            .flatMap { team -> team.players }
            .map { player -> PlayerRef.create(player) }
            .toSet()
    }
}

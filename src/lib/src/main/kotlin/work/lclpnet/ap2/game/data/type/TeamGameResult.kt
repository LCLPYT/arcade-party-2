package work.lclpnet.ap2.game.data.type

import work.lclpnet.ap2.game.data.DataContainer
import work.lclpnet.ap2.game.data.GenericGameResult
import work.lclpnet.ap2.game.data.SubjectRefResolver
import work.lclpnet.ap2.game.team.Team
import java.util.*

class TeamGameResult(
    data: DataContainer<Team, TeamRef>,
    refResolver: SubjectRefResolver<Team, TeamRef>
) : GenericGameResult<TeamRef> {
    override val playerResults: List<Pair<PlayerRef, Int>>
    override val subjectResults: List<Pair<TeamRef, Int>>
    val playerTeams: MutableMap<PlayerRef, TeamRef>
    override val winningPlayers: Set<PlayerRef>
    override val winningSubjects: Set<TeamRef>

    init {
        val byRank: List<Set<Pair<TeamRef, Int>>> = data.streamEntriesRanked().toList()

        this.subjectResults = byRank.stream()
            .flatMap { it.stream() }
            .toList()

        val playerResults = ArrayList<Pair<PlayerRef, Int>>()
        val playerTeams = LinkedHashMap<PlayerRef, TeamRef>()

        for ((teamRef, rank) in this.subjectResults) {
            val team = refResolver.resolve(teamRef) ?: continue

            for (player in team.players) {
                val ref = PlayerRef.create(player)
                playerResults.add(ref to rank)
                playerTeams[ref] = teamRef
            }
        }

        this.playerResults = playerResults.toList()
        this.playerTeams = Collections.unmodifiableMap(playerTeams)

        this.winningSubjects = when {
            byRank.isEmpty() -> emptySet()
            else -> byRank.first()
                .map { (ref, _) -> ref }
                .toSet()
        }

        this.winningPlayers = winningSubjects
            .mapNotNull { ref -> refResolver.resolve(ref) }
            .flatMap { team -> team.players }
            .map { player -> PlayerRef.create(player) }
            .toSet()
    }
}

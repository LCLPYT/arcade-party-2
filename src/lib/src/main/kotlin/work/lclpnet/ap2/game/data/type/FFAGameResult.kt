package work.lclpnet.ap2.game.data.type

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.DataContainer
import work.lclpnet.ap2.game.data.GenericGameResult

class FFAGameResult(data: DataContainer<ServerPlayer, PlayerRef>) : GenericGameResult<PlayerRef> {

    override val subjectResults: List<Pair<PlayerRef, Int>>

    override val winningSubjects: Set<PlayerRef>

    override val winningPlayers: Set<PlayerRef>
        get() = winningSubjects

    override val playerResults: List<Pair<PlayerRef, Int>>
        get() = subjectResults

    init {
        val byRank: List<Set<Pair<PlayerRef, Int>>> = data.streamEntriesRanked().toList()

        this.subjectResults = byRank
            .flatten()
            .toList()

        this.winningSubjects = when {
            byRank.isEmpty() -> emptySet()
            else -> byRank.first()
                .map { (ref, _) -> ref }
                .toSet()
        }
    }
}

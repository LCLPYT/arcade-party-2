package work.lclpnet.ap2.game.data.type

import it.unimi.dsi.fastutil.objects.ObjectIntPair
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.api.game.data.GenericGameResult

class FFAGameResult(data: DataContainer<ServerPlayer, PlayerRef>) : GenericGameResult<PlayerRef> {

    override val subjectResults: List<ObjectIntPair<PlayerRef>>

    override val winningSubjects: Set<PlayerRef>

    override val winningPlayers: Set<PlayerRef>
        get() = winningSubjects

    override val playerResults: List<ObjectIntPair<PlayerRef>>
        get() = subjectResults

    init {
        val byRank: List<Set<ObjectIntPair<PlayerRef>>> = data.streamEntriesRanked().toList()

        this.subjectResults = byRank
            .flatten()
            .toList()

        this.winningSubjects = when {
            byRank.isEmpty() -> emptySet()
            else -> byRank.first()
                .map { it.left() }
                .toSet()
        }
    }
}

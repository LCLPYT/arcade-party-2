package work.lclpnet.ap2.api.stats

import work.lclpnet.ap2.game.GameInfo
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.game.map.GameMap
import kotlin.time.Duration
import kotlin.time.Instant

data class GameSummary(
    val game: GameInfo,
    val map: GameMap?,
    val start: Instant,
    val end: Instant,
    val participants: Set<PlayerRef>
) {
    val duration: Duration = end - start
}
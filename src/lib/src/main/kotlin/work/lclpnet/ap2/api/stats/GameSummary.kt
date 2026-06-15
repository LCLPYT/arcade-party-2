package work.lclpnet.ap2.api.stats

import work.lclpnet.ap2.game.GameInfo
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.game.map.GameMap
import kotlin.time.Duration
import kotlin.time.Instant

data class GameSummary(
    val game: GameInfo,
    val minecraftVersion: String,
    val levelInfo: LevelInfo,
    val start: Instant,
    val end: Instant,
    val participants: Set<PlayerRef>
) {
    val duration: Duration = end - start
}

data class LevelInfo(
    val map: GameMap?,
    val seed: Long?,
)
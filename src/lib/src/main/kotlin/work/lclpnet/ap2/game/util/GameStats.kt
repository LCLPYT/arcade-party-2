package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreEventSource
import work.lclpnet.ap2.game.data.type.PlayerRef

/**
 * Creates a [work.lclpnet.ap2.api.stats.StatsManager] that tracks the score given by the game int score data container and additional stats.
 * @param data The int score data container. Automatically syncs the score stat.
 * @param stats The additional stats to track. Must be updated by the game implementation.
 * @return The [work.lclpnet.ap2.api.stats.FFAStatsManager] to track stats.
 */
fun MiniGameInstance.createStats(
    winManager: WinManager<ServerPlayer, PlayerRef>,
    data: IntScoreEventSource<ServerPlayer>,
    vararg stats: Stat<out Any>
): FFAStatsManager {
    val manager = FFAStatsManager(buildSet {
        add(CommonStats.Score)
        addAll(stats)
    })

    data.register { player, score ->
        manager.set(player, CommonStats.Score, score)
    }

    winManager.setStatsManager(manager)

    return manager
}

fun MiniGameInstance.createStats(winManager: WinManager<ServerPlayer, PlayerRef>, vararg stats: Stat<out Any>): FFAStatsManager {
    val set = setOf(*stats)
    val manager = FFAStatsManager(set)

    winManager.setStatsManager(manager)

    return manager
}

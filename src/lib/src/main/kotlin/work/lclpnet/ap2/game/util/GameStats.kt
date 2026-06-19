package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.TeamStatsManager
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.ScoreListenerView
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.data.type.TeamRef
import work.lclpnet.ap2.game.team.Team

/**
 * Creates a [work.lclpnet.ap2.api.stats.StatsManager] that tracks the score given by the game int score data container and additional stats.
 * @param data The int score data container. Automatically syncs the score stat.
 * @param stats The additional stats to track. Must be updated by the game implementation.
 * @return The [work.lclpnet.ap2.api.stats.FFAStatsManager] to track stats.
 */
fun <T : Any> useFFAStats(
    winManager: WinManager<ServerPlayer, PlayerRef>,
    data: ScoreListenerView<ServerPlayer, T>,
    scoreStat: Stat<T>,
    stats: Iterable<Stat<out Any>>
): FFAStatsManager {
    // the score is shown as the data container detail next to each ranking entry, so it is tracked
    // for the backend but not rendered as its own stat section
    val hiddenScore = scoreStat.copy(display = false)

    val manager = useFFAStats(winManager, buildSet {
        add(hiddenScore)
        addAll(stats)
    })

    data.register { player, score ->
        manager.set(player, hiddenScore, score)
    }

    return manager
}

fun useFFAStats(
    winManager: WinManager<ServerPlayer, PlayerRef>,
    stats: Iterable<Stat<out Any>>
): FFAStatsManager {
    val set = stats.distinctBy { it.id }.toSet()

    val manager = FFAStatsManager(set)

    winManager.setStatsManager(manager)

    return manager
}

fun <T : Any> MiniGameInstance.useTeamStats(
    winManager: WinManager<Team, TeamRef>,
    teamScore: ScoreListenerView<Team, T>,
    scoreStat: Stat<T>,
    teamStats: Iterable<Stat<out Any>>,
    memberStats: Iterable<Stat<out Any>>
): TeamStatsManager {
    // the score is shown as the data container detail next to each ranking entry, so it is tracked
    // for the backend but not rendered as its own stat section
    val hiddenScore = scoreStat.copy(display = false)

    val manager = useTeamStats(
        winManager,
        buildSet {
            add(hiddenScore)
            addAll(teamStats)
        },
        memberStats
    )

    teamScore.register { team, score ->
        manager.teams.set(team, hiddenScore, score)
    }

    return manager
}

fun MiniGameInstance.useTeamStats(
    winManager: WinManager<Team, TeamRef>,
    teamStats: Iterable<Stat<out Any>>,
    playerStats: Iterable<Stat<out Any>>
): TeamStatsManager {
    val manager = TeamStatsManager(
        teamStats.distinctBy { it.id }.toSet(),
        playerStats.distinctBy { it.id }.toSet()
    ) { team ->
        TeamRef(team.key, gameHandle.translations)
    }

    winManager.setStatsManager(manager)

    return manager
}

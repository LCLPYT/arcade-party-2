package work.lclpnet.ap2.mode_default.util

import work.lclpnet.ap2.api.base.GameQueue
import work.lclpnet.ap2.api.music.SongCache
import work.lclpnet.ap2.api.stats.SessionStatsRecorder
import work.lclpnet.ap2.game.color.PlayerColorPreferences
import work.lclpnet.ap2.game.player.PlayerManager
import work.lclpnet.ap2.mode_default.ApMiniGameArgs
import work.lclpnet.ap2.mode_default.cmd.ForceGameCommand
import work.lclpnet.ap2.util.AssetManager
import work.lclpnet.ap2.util.TablistManager
import work.lclpnet.game.api.GameFinisher

/**
 * A container for objects required for the arcade-party base game.
 * In contrast to the [work.lclpnet.ap2.mode_default.ApMiniGameArgs], which contains objects required for the mini-games on a standalone level,
 * this class contains objects required for the actual "base" of arcade-party.
 * The "base" of arcade-party refers to the default type of game cycle, where players collect points earned from mini-games.
 * That means, this class contains stuff like the [ScoreManager] that actually manages the accumulation of points,
 * the [work.lclpnet.ap2.api.base.GameQueue] that holds information about which games are played next and the [work.lclpnet.ap2.game.player.PlayerManager] that keeps track
 * of which state players are in.
 */
data class ApBaseArgs(
    val miniGameArgs: ApMiniGameArgs,
    val gameQueue: GameQueue,
    val playerManager: PlayerManager,
    val colorPreferences: PlayerColorPreferences,
    val forceGameCommand: ForceGameCommand,
    val sharedSongCache: SongCache,
    val scoreManager: ScoreManager,
    val finisher: GameFinisher,
    val stats: SessionStatsRecorder,
    val tablistManager: TablistManager,
    val assetManager: AssetManager,
    val activitySwitcher: ActivitySwitcher
)
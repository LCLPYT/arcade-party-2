package work.lclpnet.ap2.game

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import org.slf4j.Logger
import work.lclpnet.activity.util.BossBarHandler
import work.lclpnet.ap2.api.base.WorldBorderManager
import work.lclpnet.ap2.api.data.DataManager
import work.lclpnet.ap2.api.game.MiniGameResults
import work.lclpnet.ap2.api.map.MapFacade
import work.lclpnet.ap2.api.music.SongCache
import work.lclpnet.ap2.api.music.SongManager
import work.lclpnet.ap2.api.stats.StatsResult
import work.lclpnet.ap2.game.color.PlayerColorPreferences
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.player.PlayerRankView
import work.lclpnet.ap2.game.team.TeamConfig
import work.lclpnet.ap2.game.util.PlayerUtil
import work.lclpnet.ap2.impl.util.DeathMessages
import work.lclpnet.ap2.impl.util.world.SubWorldManager
import work.lclpnet.ap2.util.AssetManager
import work.lclpnet.ap2.util.FontService
import work.lclpnet.ap2.util.ServerViewDistanceManager
import work.lclpnet.ap2.util.TablistManager
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardManager
import work.lclpnet.game.api.WorldFacade
import work.lclpnet.game.impl.prot.MutableProtectionConfig
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.bossbar.BossBarProvider
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.time.Instant

interface MiniGameHandle {

    val server: MinecraftServer

    val gameInfo: GameInfo

    val logger: Logger

    val worldFacade: WorldFacade

    val mapFacade: MapFacade

    val hooks: HookRegistrar

    val commands: CommandRegistrar

    /**
     * Get the mini-game root scheduler.
     * This scheduler is stopped when the mini-game terminates and can be used for every important scheduler task.
     * Game-logic related tasks should be scheduled with the game scheduler instead, as it is stopped when someone wins.
     * @return The root scheduler for this mini-game.
     */
    val rootScheduler: TaskScheduler

    /**
     * Get the game scheduler, which is a child of the game root scheduler (obtained by [.getRootScheduler]).
     * This scheduler can be reset and is automatically stopped, the moment someone wins the game.
     * In contrast, the game root scheduler is only stopped when the mini-game terminates.
     * @return The game scheduler for game logic.
     */
    val scheduler: TaskScheduler

    val translations: Translations

    val participants: Participants

    val colorPreferences: PlayerColorPreferences

    val worldBorderManager: WorldBorderManager

    val playerUtil: PlayerUtil

    val bossBarProvider: BossBarProvider

    val bossBarHandler: BossBarHandler

    val scoreboardManager: CustomScoreboardManager

    val teamConfig: Optional<TeamConfig>

    val songManager: SongManager

    val sharedSongCache: SongCache

    val deathMessages: DeathMessages

    val dataManager: DataManager

    val subWorldManager: SubWorldManager

    val tablistManager: TablistManager

    val assetManager: AssetManager

    val fontService: FontService

    val startTime: Instant

    val rankView: PlayerRankView

    val viewDistanceManager: ServerViewDistanceManager

    fun resetGameScheduler()

    fun protect(action: Consumer<MutableProtectionConfig>)

    fun whenDone(action: Runnable)

    fun complete(results: MiniGameResults)

    val isFinale: Boolean

    fun setWorld(world: ServerLevel)

    /**
     * Submit the game stats to the stats backend.
     * This can only be done once per instance.
     * @param stats The stats to submit.
     * @return The stats record id future. Uniquely identifies the submitted stats record when present.
     */
    fun submitStats(stats: StatsResult): CompletableFuture<UUID>
}
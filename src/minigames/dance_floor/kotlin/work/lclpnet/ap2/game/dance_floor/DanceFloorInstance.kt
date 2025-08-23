package work.lclpnet.ap2.game.dance_floor

import net.minecraft.server.world.ServerWorld
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.music.ConfiguredSong
import work.lclpnet.ap2.api.music.SongWrapper
import work.lclpnet.ap2.game.dance_floor.cmd.SetSongCommand
import work.lclpnet.ap2.game.dance_floor.cmd.SkipSongCommand
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.music.SongHandler
import work.lclpnet.ap2.impl.util.Hints
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.lobby.game.map.GameMap
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

private val INITIAL_MAX_TICKS = Ticks.seconds(16)
private val INITIAL_MIN_TICKS = Ticks.seconds(10)
private val DELAY_HALF_LIFE = Ticks.minutes(1)
private val TOTAL_MIN_DELAY = Ticks.seconds(2)

// TOTAL_MIN_DALY will be reached after the following amount of seconds:
// DELAY_HALF_LIFE * log((INITIAL_MAX_TICKS + INITIAL_MIN_TICKS) * 0.5 / TOTAL_MIN_DELAY) / log(2)

private val DELAY_RATIO = (INITIAL_MAX_TICKS - INITIAL_MIN_TICKS) / INITIAL_MAX_TICKS.toFloat()

class DanceFloorInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    val random = Random()
    val songHandler = SongHandler(gameHandle, random)
    var loadingSong: CompletableFuture<ConfiguredSong>? = null
    var currentSong: SongWrapper? = null
    var songProgress: Int? = null
    var totalDurationTicks = 0

    init {
        useRemainingPlayersDisplay()
    }

    override fun createWorldBootstrap(world: ServerWorld, map: GameMap): CompletableFuture<Void> {
        return songHandler.loadSongs(gameHandle.gameInfo.id)
    }

    override fun prepare() {
        SetSongCommand(songHandler, this::nextSong).register(gameHandle.commands)
        SkipSongCommand(this::nextSong).register(gameHandle.commands)

        Hints(gameHandle).sendBeforeReady(this, Hints.Mod.NOTICA)
    }

    override fun ready() {
        nextSong()
    }

    @Synchronized
    fun nextSong() {
        if (loadingSong != null) return

        loadingSong = songHandler.loadNextSong()
        currentSong?.stop()

        loadingSong!!.thenAccept(::playSong)
    }

    @Synchronized
    fun playSong(song: ConfiguredSong) {
        val startTick = if (songProgress != null) songProgress!! else song.info.meta.startTick.orElse(0)

        currentSong = songHandler.play(song, gameHandle.server, startTick)
        loadingSong = null

        val delayTicks = sampleDelayTicks(totalDurationTicks)

        gameHandle.gameScheduler.timeout(delayTicks) { ->
            totalDurationTicks += delayTicks
            stopMusic()
        }
    }

    fun stopMusic() {
        currentSong?.stop()
    }

    fun sampleDelayTicks(timeTicks: Int): Int {
        val decay = exp(-timeTicks.toDouble() * ln(2.0) / DELAY_HALF_LIFE)

        // average shrinks exponentially
        val mean = (INITIAL_MIN_TICKS + INITIAL_MAX_TICKS) * 0.5 * decay

        // reconstruct min and max from avg and ratio
        val halfRange = mean * DELAY_RATIO
        val min = (mean - halfRange).roundToInt().coerceAtLeast(TOTAL_MIN_DELAY)
        val max = (mean + halfRange).roundToInt().coerceAtLeast(min)

        return random.nextInt(max - min + 1) + min
    }
}
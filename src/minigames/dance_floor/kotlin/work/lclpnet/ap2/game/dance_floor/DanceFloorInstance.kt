package work.lclpnet.ap2.game.dance_floor

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.DyeColor
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.music.ConfiguredSong
import work.lclpnet.ap2.api.music.SongWrapper
import work.lclpnet.ap2.game.dance_floor.cmd.SetSongCommand
import work.lclpnet.ap2.game.dance_floor.cmd.SkipSongCommand
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.music.SongHandler
import work.lclpnet.ap2.impl.util.BlockHelper
import work.lclpnet.ap2.impl.util.Hints
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.ap2.players
import work.lclpnet.ap2.setBlock
import work.lclpnet.ap2.timeout
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.lobby.game.map.GameMap
import java.util.concurrent.CompletableFuture
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.asJavaRandom

private val INITIAL_DELAY_MAX_TICKS = Ticks.seconds(16)
private val INITIAL_DELAY_MIN_TICKS = Ticks.seconds(10)
private val DELAY_HALF_LIFE = Ticks.minutes(1)
private val TOTAL_MIN_DELAY_TICKS = Ticks.seconds(2)

// TOTAL_MIN_DALY will be reached after the following amount of seconds:
// DELAY_HALF_LIFE * log((INITIAL_MAX_TICKS + INITIAL_MIN_TICKS) * 0.5 / TOTAL_MIN_DELAY) / log(2)

private val DELAY_RATIO = (INITIAL_DELAY_MAX_TICKS - INITIAL_DELAY_MIN_TICKS) / INITIAL_DELAY_MAX_TICKS.toFloat()

class DanceFloorInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    val songHandler = SongHandler(gameHandle, Random.asJavaRandom())
    var loadingSong: CompletableFuture<ConfiguredSong>? = null
    var currentSong: SongWrapper? = null
    var songProgress: Int? = null
    var totalDurationTicks = 0
    val existingColors = mutableSetOf<DyeColor>()
    var task: TaskHandle? = null

    init {
        useRemainingPlayersDisplay()
        useSurvivalMode()
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
        eliminateBelowCriticalHeight()
        nextSong()
    }

    @Synchronized
    fun nextSong() {
        loadingSong?.cancel(true)
        currentSong?.stop()
        loadingSong = songHandler.loadNextSong()

        nextCycle()
    }

    private fun nextCycle() {
        loadingSong?.thenAccept(::playSong)

        randomizeBlocks()

        // remove blocks from hotbar
        for (player in players()) {
            for (i in 0..8) {
                player.inventory.setStack(i, ItemStack.EMPTY)
            }
        }
    }

    private fun randomizeBlocks() {
        for (pos in floor()) {
            val color = DyeColor.entries.random()
            existingColors.add(color)
            world.setBlock(pos, BlockHelper.getWool(color))
        }
    }

    private fun floor(): BlockShape = MapUtil.readShape(map, "floor")!!

    @Synchronized
    fun playSong(song: ConfiguredSong) {
        val startTick = if (songProgress != null) songProgress!! else song.info.meta.startTick.orElse(0)

        currentSong = songHandler.play(song, gameHandle.server, startTick, songProgress == null)

        val delayTicks = sampleDelayTicks(totalDurationTicks)

        task = timeout(delayTicks) { ->
            totalDurationTicks += delayTicks
            songProgress = startTick + delayTicks
            stopMusic()
        }
    }

    fun stopMusic() {
        currentSong?.stop()

        // give players the correct wool to compare with the floor
        val block = BlockHelper.getWool(existingColors.random())

        for (player in players()) {
            for (i in 0..8) {
                player.inventory.setStack(i, ItemStack(block))
            }
        }

        SoundHelper.playSound(world, SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 0.9f, 0f)

        task = timeout(seconds = 4) { ->
            SoundHelper.playSound(world, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.HOSTILE, 0.4f, 1.4f)
            removeBlocks(block)
        }
    }

    fun removeBlocks(except: Block) {
        for (pos in floor()) {
            if (world.getBlockState(pos)!!.isOf(except)) continue

            world.setBlock(pos, Blocks.AIR)
        }

        task = timeout(seconds = 5) { ->
            SoundHelper.playSound(world, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.5f, 1f)
            nextCycle()
        }
    }

    fun sampleDelayTicks(timeTicks: Int): Int {
        val decay = exp(-timeTicks.toDouble() * ln(2.0) / DELAY_HALF_LIFE)

        // average shrinks exponentially
        val mean = (INITIAL_DELAY_MIN_TICKS + INITIAL_DELAY_MAX_TICKS) * 0.5 * decay

        // reconstruct min and max from avg and ratio
        val halfRange = mean * DELAY_RATIO
        val min = (mean - halfRange).roundToInt().coerceAtLeast(TOTAL_MIN_DELAY_TICKS)
        val max = (mean + halfRange).roundToInt().coerceAtLeast(min)

        return Random.nextInt(max - min + 1) + min
    }

    override fun onEliminated(player: ServerPlayerEntity?) {
        SoundHelper.playSoundAt(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1f, 0f)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.LAVA, 100, 0.5, 0.5, 0.5, 0.2)
    }
}
package work.lclpnet.ap2.game.dance_floor

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.scoreboard.AbstractTeam
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import work.lclpnet.ap2.*
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.music.ConfiguredSong
import work.lclpnet.ap2.api.music.SongWrapper
import work.lclpnet.ap2.game.dance_floor.cmd.SetSongCommand
import work.lclpnet.ap2.game.dance_floor.cmd.SkipSongCommand
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.music.SongHandler
import work.lclpnet.ap2.impl.util.*
import work.lclpnet.ap2.impl.util.handler.Visibility
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler
import work.lclpnet.ap2.impl.util.handler.VisibilityManager
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.lobby.game.map.GameMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.asJavaRandom

private val MIN_DELAY_TICKS = Ticks.seconds(10)
private val MAX_DELAY_TICKS = Ticks.seconds(15)

private val INITIAL_BLOCK_DELAY_TICKS = Ticks.seconds(5)
private const val BLOCK_DELAY_TICKS_DECREASE_PER_MINUTE = 26
private const val TOTAL_MIN_BLOCK_DELAY_TICKS = 5

private const val PARTICLE_AMOUNT = 3

class DanceFloorInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    val songHandler = SongHandler(gameHandle, Random.asJavaRandom())
    var loadingSong: CompletableFuture<ConfiguredSong>? = null
    var currentSong: SongWrapper? = null
    var songProgress: Int? = null
    var totalDurationTicks = 0
    var task: TaskHandle? = null
    var newSong = true
    var blockRandomizer: BlockRandomizer? = null

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

        blockRandomizer = BlockRandomizer(floorShape(), world)

        preloadNextSong()
        setupTeam()
    }

    override fun ready() {
        eliminateBelowCriticalHeight()
        nextCycle()

        val particleShape = MapUtil.readShape(map, "particle-shape")

        interval(7) { ->
            if (currentSong == null) return@interval

            repeat(PARTICLE_AMOUNT) {
                val pos = particleShape.bounds().randomPos(Random.asJavaRandom())
                world.spawnParticles(ParticleTypes.NOTE, pos, 10, 3.0, 2.0, 3.0, 1.0)
            }
        }

        interval(1) { ->
            totalDurationTicks++
        }
    }

    fun setupTeam() {
        val scoreboardManager = gameHandle.getScoreboardManager()
        val team = scoreboardManager.createTeam("team")
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER)
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team)

        val visibility = VisibilityHandler(
            VisibilityManager(team, Visibility.VISIBLE),
            gameHandle.translations,
            gameHandle.participants
        )

        visibility.init(gameHandle.getHooks())

        visibility.giveItems()
    }

    @Synchronized
    fun nextSong() {
        task?.cancel()

        resetSong()
        preloadNextSong()

        nextCycle()
    }

    private fun nextCycle() {
        task?.cancel()

        synchronized(this) {
            loadingSong?.thenAccept(::playSong)
        }

        blockRandomizer?.randomizeBlocks()

        for (player in players()) {
            player.inventory.setStack(4, ItemStack.EMPTY)
        }
    }

    private fun floorShape(): BlockShape = MapUtil.readShape(map, "floor")!!

    @Synchronized
    fun playSong(song: ConfiguredSong) {
        val startTick = if (songProgress != null) songProgress!! else song.info.meta.startTick.orElse(0)

        currentSong = songHandler.play(song, gameHandle.server, startTick, newSong, true)
        newSong = false

        // check how many song-ticks are left
        val totalSongTicks = song.checkedSong.song.lastNoteTick().orElseGet { song.checkedSong.song.durationTicks() }
        val remainingSongTicks = totalSongTicks - startTick

        // sample a random duration in game ticks
        val randomDelayTicks = Random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1) + MIN_DELAY_TICKS

        // convert to song ticks, limited by remaining song ticks
        val songTicks = song.checkedSong.song.tempo()
            .durationTicks(startTick, randomDelayTicks / 20f)
            .coerceAtMost(remainingSongTicks)

        // convert clamped back to game time
        val delaySeconds = song.checkedSong.song.tempo().durationSeconds(startTick, songTicks)
        val delayTicks = delaySeconds.times(20).roundToInt().coerceAtLeast(0)

        task = timeout(delayTicks) { ->
            val progress = startTick + songTicks

            if (progress >= totalSongTicks) {
                resetSong()
                preloadNextSong()
            } else {
                songProgress = progress
            }

            stopMusic()
        }
    }

    private fun resetSong() {
        loadingSong?.cancel(true)
        currentSong?.stop()
        currentSong = null
        loadingSong = null
        newSong = true
        songProgress = null
    }

    @Synchronized
    private fun preloadNextSong(): CompletableFuture<ConfiguredSong> {
        var current = loadingSong

        if (current != null)
            return current

        current = songHandler.loadNextSong()
        loadingSong = current

        return current
    }

    @Synchronized
    fun stopMusic() {
        currentSong?.stop()
        currentSong = null

        // give players the correct wool to compare with the floor
        val dyeColor = blockRandomizer!!.existingColors.random()
        val block = BlockHelper.getWool(dyeColor)

        for (player in players()) {
            player.inventory.setStack(4, ItemStack(block))
            player.setSelectedSlot(4)
        }

        SoundHelper.playSound(world, SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 0.9f, 0f)

        val decreaseTicks = (totalDurationTicks * BLOCK_DELAY_TICKS_DECREASE_PER_MINUTE / Ticks.minutes(1).toFloat())
            .roundToInt()
            .coerceAtLeast(0)

        val blockDelayTicks = max(TOTAL_MIN_BLOCK_DELAY_TICKS, INITIAL_BLOCK_DELAY_TICKS - decreaseTicks)

        task = timeout(blockDelayTicks) { ->
            SoundHelper.playSound(world, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.HOSTILE, 0.4f, 0.8f)
            removeBlocks(block)
        }

        translate("game.ap2.dance_floor.stand_on", TextUtil.getVanillaName(block))
            .withColor(dyeColor.signColor)
            .sendTo(players(), true)
    }

    fun removeBlocks(except: Block) {
        for (pos in floorShape()) {
            if (world.getBlockState(pos)!!.isOf(except)) continue

            world.setBlock(pos, Blocks.AIR)
        }

        task = timeout(seconds = 4) { ->
            SoundHelper.playSound(world, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.5f, 1f)
            nextCycle()
        }
    }

    override fun onEliminated(player: ServerPlayerEntity?) {
        SoundHelper.playSoundAt(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1f, 0f)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.LAVA, 100, 0.5, 0.5, 0.5, 0.2)
    }
}
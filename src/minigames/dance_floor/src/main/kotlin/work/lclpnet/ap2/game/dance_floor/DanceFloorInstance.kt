package work.lclpnet.ap2.game.dance_floor

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.scores.Team
import org.json.JSONObject
import work.lclpnet.ap2.api.music.ConfiguredSong
import work.lclpnet.ap2.api.music.SongWrapper
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.*
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.dance_floor.cmd.SetSongCommand
import work.lclpnet.ap2.game.dance_floor.cmd.SkipSongCommand
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.game.PlayerUtil
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.music.SongHandler
import work.lclpnet.ap2.impl.util.*
import work.lclpnet.ap2.impl.util.handler.Visibility
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler
import work.lclpnet.ap2.impl.util.handler.VisibilityManager
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.asJavaRandom

private val MIN_DELAY_TICKS = Ticks.seconds(6)
private val MAX_DELAY_TICKS = Ticks.seconds(10)

private const val INITIAL_BLOCK_DELAY_TICKS = 66
private const val BLOCK_DELAY_TICKS_DECREASE_PER_MINUTE = 18
private const val TOTAL_MIN_BLOCK_DELAY_TICKS = 5
private const val NEXT_ROUND_INITIAL_TICKS = 80
private const val NEXT_ROUND_TICKS_DECREASE_PER_MINUTE = 40
private const val NEXT_ROUND_MIN_TICKS = 35

private const val PARTICLE_AMOUNT = 3

class DanceFloorInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    val songHandler: SongHandler,
) : EliminationGameInstance(gameHandle, level, map) {

    val eliminate = mutableSetOf<ServerPlayer>()
    var loadingSong: CompletableFuture<ConfiguredSong>? = null
    var currentSong: SongWrapper? = null
    var songProgress: Int? = null
    var totalDurationTicks = 0
    var task: TaskHandle? = null
    var newSong = true
    var blockRandomizer: BlockRandomizer? = null
    var spectatorSpawns = mutableListOf<PositionRotation>()
    var visibilityManager: VisibilityManager? = null
    var delayTicks = MAX_DELAY_TICKS
    var round = 1

    init {
        useRemainingPlayersDisplay()
        useSurvivalMode()
        disableTeleportEliminated()
    }

    override fun prepare() {
        SetSongCommand(songHandler, this::nextSong).register(gameHandle.commands)
        SkipSongCommand(this::nextSong).register(gameHandle.commands)

        Hints(gameHandle).sendBeforeReady(this, Hints.Mod.NOTICA)

        blockRandomizer = BlockRandomizer(floorShape(), level)

        preloadNextSong()
        setupTeam()

        readSpectatorSpawns()
    }

    private fun readSpectatorSpawns() {
        for (item in map.properties.getJSONArray("spectator-spawns")) {
            if (item !is JSONObject) continue

            val spawn = MapUtil.readCenteredVec3d(item.getJSONArray("spawn"))
            val yaw = MapUtil.readAngle(item.optNumber("yaw", 0))

            spectatorSpawns.add(PositionRotation(spawn.x, spawn.y, spawn.z, yaw, 0f))
        }
    }

    override fun go() {
        commons().whenBelowCriticalHeight().then { player -> softEliminate(player) }
        nextCycle()

        val particleShape = MapUtil.readShape(map, "particle-shape")

        interval(7) {
            if (currentSong == null) return@interval

            repeat(PARTICLE_AMOUNT) {
                val pos = particleShape.bounds().randomPos(Random.asJavaRandom())
                level.spawnParticles(ParticleTypes.NOTE, pos, 10, 3.0, 2.0, 3.0, 1.0)
            }
        }

        interval(1) {
            totalDurationTicks++
        }
    }

    fun softEliminate(player: ServerPlayer) {
        if (!gameHandle.participants.isParticipating(player) || eliminate.contains(player)) return

        SoundHelper.playSoundAt(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 0f)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.LAVA, 100, 0.5, 0.5, 0.5, 0.2)

        gameHandle.playerUtil.resetPlayer(player)
        visibilityManager?.setVisibilityFor(player, Visibility.VISIBLE)

        val pos = spectatorSpawns.randomOrNull()

        if (pos != null) {
            player.setGameMode(GameType.ADVENTURE)
            player.teleport(pos)
        } else {
            player.setGameMode(GameType.SPECTATOR)
        }

        eliminate.add(player)
    }

    fun setupTeam() {
        val scoreboardManager = gameHandle.scoreboardManager
        val team = scoreboardManager.createTeam("team")
        team.collisionRule = Team.CollisionRule.NEVER
        scoreboardManager.joinTeam(players(), team)

        visibilityManager = VisibilityManager(team, Visibility.VISIBLE)
        val visibility = VisibilityHandler(visibilityManager, gameHandle.translations, gameHandle.participants)

        visibility.init(hooks)

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
            player.inventory.setItem(4, ItemStack.EMPTY)
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

        // convert to song ticks, limited by remaining song ticks
        val songTicks = song.checkedSong.song.tempo()
            .durationTicks(startTick, delayTicks / 20f)
            .coerceAtMost(remainingSongTicks)

        // decrease ticks
        val decrease = round(20f / round.toFloat()).toInt()
        delayTicks = max(MIN_DELAY_TICKS, delayTicks - decrease)
        round++

        // convert clamped back to game time
        val delaySeconds = song.checkedSong.song.tempo().durationSeconds(startTick, songTicks)
        val realDelayTicks = delaySeconds.times(20).roundToInt().coerceAtLeast(0)

        task = timeout(realDelayTicks) {
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
            player.inventory.setItem(4, ItemStack(block))
            player.setSelectedSlot(4)
        }

        SoundHelper.playSound(level, SoundEvents.IRON_GOLEM_HURT, SoundSource.HOSTILE, 0.9f, 0f)

        val decreaseTicks = (totalDurationTicks * BLOCK_DELAY_TICKS_DECREASE_PER_MINUTE / Ticks.minutes(1).toFloat())
            .roundToInt()
            .coerceAtLeast(0)

        val blockDelayTicks = max(TOTAL_MIN_BLOCK_DELAY_TICKS, INITIAL_BLOCK_DELAY_TICKS - decreaseTicks)

        task = timeout(blockDelayTicks) {
            SoundHelper.playSound(level, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 0.4f, 0.8f)
            removeBlocks(block)
        }

        translate("game.ap2.dance_floor.stand_on", TextUtil.getVanillaName(block))
            .withColor(dyeColor.textColor)
            .sendTo(players(), true)
    }

    fun removeBlocks(except: Block) {
        for (pos in floorShape()) {
            if (level.getBlockState(pos).isOf(except)) continue

            level.setBlock(pos, Blocks.AIR)
        }

        val decreaseTicks = (totalDurationTicks * NEXT_ROUND_TICKS_DECREASE_PER_MINUTE / Ticks.minutes(1).toFloat())
            .roundToInt()
            .coerceAtLeast(0)

        val nextRoundTicks = max(NEXT_ROUND_MIN_TICKS, NEXT_ROUND_INITIAL_TICKS - decreaseTicks)

        task = timeout(ticks = nextRoundTicks) {
            SoundHelper.playSound(level, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.5f, 1f)
            checkEliminated()
        }
    }

    private fun checkEliminated() {
        if (!eliminate.isEmpty()) {
            eliminate.forEach { player -> gameHandle.playerUtil.setStateOverride(player, PlayerUtil.State.DEFAULT) }
            eliminateAll(eliminate)
        }

        if (winManager.isGameOver) return

        nextCycle()
    }
}
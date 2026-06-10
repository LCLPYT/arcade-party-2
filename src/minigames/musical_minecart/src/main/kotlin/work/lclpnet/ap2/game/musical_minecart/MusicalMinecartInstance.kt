package work.lclpnet.ap2.game.musical_minecart

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.frog.Frog
import net.minecraft.world.entity.animal.frog.FrogVariant
import net.minecraft.world.entity.vehicle.minecart.Minecart
import net.minecraft.world.phys.Vec3
import org.json.JSONArray
import work.lclpnet.ap2.api.music.ConfiguredSong
import work.lclpnet.ap2.core.type.ApVariantHolder
import work.lclpnet.ap2.ext.runAfter
import work.lclpnet.ap2.ext.runEvery
import work.lclpnet.ap2.ext.ticks
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.EliminationGameInstance
import work.lclpnet.ap2.game.musical_minecart.cmd.SetSongCommand
import work.lclpnet.ap2.game.musical_minecart.cmd.SkipSongCommand
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.music.SongHandler
import work.lclpnet.ap2.impl.util.Hints
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.entity.EntityDismountCallback
import work.lclpnet.kibu.hook.entity.EntityMountCallback
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.notica.Notica
import work.lclpnet.notica.api.PlaybackOptions
import work.lclpnet.notica.api.PlaybackVariant
import work.lclpnet.notica.api.SongHandle
import work.lclpnet.notica.api.StereoMode
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val DEBUG_INFINITE_SONGS = false
private const val DEBUG_FULL_DELAY = false
private const val DEBUG_INFO = false

private val MIN_DELAY_TICKS = Ticks.seconds(10L)
private val MAX_DELAY_TICKS = Ticks.seconds(20L)
private val NEXT_SONG_DELAY = 2.seconds
private const val PARTICLE_AMOUNT = 2
private const val MAX_DECOYS = 4
private const val DECOY_CHANCE = 0.15f

class MusicalMinecartInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    private val random: Random,
    private val songs: SongHandler,
) : EliminationGameInstance(gameHandle, level, map) {

    private val minecartEntities = HashSet<Minecart>()
    private val ready = AtomicBoolean(false)
    private var intermission = false
    private lateinit var bounds: BlockBox
    private var particleBox: BlockBox? = null
    private var songHandle: SongHandle? = null
    private var timer: BossBarTimer? = null
    private var taskHandles: List<TaskHandle> = emptyList()
    private var pendingSong: CompletableFuture<ConfiguredSong>? = null
    private var eliminationDelayTicks = Ticks.seconds(9L)
    private var minecartsGlowing = false

    override fun prepare() {
        bounds = MapUtil.readBox(map.requireProperty("bounds"))

        if (map.hasProperty("particle", JSONArray::class.java)) {
            particleBox = MapUtil.readBox(map.requireProperty("particle"))
        }

        val eliminationDelayProp = map.getProperty<Any?>("elimination-delay-ticks")

        if (eliminationDelayProp is Number) {
            eliminationDelayTicks = max(0, eliminationDelayProp.toInt()).toLong()
        }

        useRemainingPlayersDisplay()

        gameHandle.protect(ProtectionTypes.MOUNT::allow)

        val commands = gameHandle.commands
        SetSongCommand(songs, ::skipSong).register(commands)
        SkipSongCommand(::skipSong).register(commands)

        Hints(gameHandle).sendBeforeReady(this, Hints.Mod.NOTICA)
    }

    override fun go() {
        nextSong()

        runEvery(7.ticks) {
            tickParticle()
        }

        ready.set(true)

        val hooks = gameHandle.hooks

        EntityMountCallback.HOOK.registerWith(hooks) { _, vehicle, _ ->
            if (vehicle.isCurrentlyGlowing) {
                vehicle.setGlowingTag(false)
            }
            false
        }

        EntityDismountCallback.HOOK.registerWith(hooks) { _, vehicle ->
            if (minecartsGlowing && vehicle is Minecart) {
                vehicle.setGlowingTag(true)
            }
            false
        }
    }

    @Synchronized
    private fun nextSong() {
        removeMinecarts()

        intermission = false

        var future = pendingSong

        if (songs.hasPrioritySongs()) {
            future = loadNextSong()
            intermission = pendingSong != null
        } else if (future == null) {
            future = loadNextSong().also { pendingSong = it }
        }

        future.whenComplete { res, err ->
            if (err == null) {
                playSong(res)
                return@whenComplete
            }

            gameHandle.logger.error("Failed to load next song", err)
            winManagerAccess.draw()
        }
    }

    @Synchronized
    private fun playSong(config: ConfiguredSong) {
        if (!intermission) {
            pendingSong = loadNextSong()
        }

        songs.pushSongHistory(config)

        val server = gameHandle.server
        val players = PlayerLookup.all(server)

        val song = config.checkedSong()
        val meta = config.info().meta()
        val finalVolume = (meta.volume().orElse(1f) ?: 1f) * SongHandler.MUSIC_VOLUME
        val stereoMode = meta.stereoMode().orElse(StereoMode.SPATIAL)

        val playbackOptions = PlaybackOptions(finalVolume, PlaybackVariant.STREAMED, stereoMode)

        val notica = Notica.getInstance(server)
        songHandle = notica.playSong(song, playbackOptions, meta.startTick().orElse(0) ?: 0, players)

        val delay = if (DEBUG_FULL_DELAY) {
            MAX_DELAY_TICKS
        } else {
            MIN_DELAY_TICKS + random.nextInt((MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1).toInt())
        }

        if (DEBUG_INFO) {
            val total = songs.songs.size
            val done = songs.queue.transfer().occurred().size
            timer = commons().createTimerTicks("Queue $done / $total", delay.toInt())
        }

        taskHandles = listOf(
            runAfter(delay.ticks) {
                stopMusic()
            }
        )

        val nowPlaying = songs.nowPlayingText(config)
        nowPlaying?.sendTo(players)
    }

    @Synchronized
    private fun loadNextSong(): CompletableFuture<ConfiguredSong> {
        return songs.loadNextSong()
    }

    @Synchronized
    private fun stopMusic() {
        songHandle?.stop()
        songHandle = null

        if (DEBUG_INFINITE_SONGS) {
            nextSong()
            return
        }

        spawnMinecarts()

        SoundHelper.playSound(gameHandle.server, SoundEvents.IRON_GOLEM_HURT, SoundSource.HOSTILE, 0.9f, 0f)

        val translations = gameHandle.translations
        val participants: Participants = gameHandle.participants

        for (player in participants) {
            val msg = Component.literal("⚠ ")
                .append(
                    translations.translateText(player, "game.ap2.musical_minecart.deadline")
                        .styled { s -> s.withColor(0xff0000).withBold(true) }
                )
                .append(" ⚠").withColor(0xffff00)

            player.sendOverlayMessage(msg)
        }

        val scheduler = gameHandle.scheduler

        taskHandles = listOf(
            scheduler.timeout(this::markFreeMinecarts, eliminationDelayTicks / 2),
            scheduler.timeout(this::eliminatePlayers, eliminationDelayTicks)
        )
    }

    private fun spawnMinecarts() {
        val p = random.nextDouble()
        var decoys = floor(ln(p) / ln(DECOY_CHANCE.toDouble())).toInt()

        decoys = decoys.coerceIn(0, MAX_DECOYS)

        var count = gameHandle.participants.count() - 1

        if (count <= 0) {
            decoys = 0
            count = 0
        }

        val total = count + decoys
        val pos = BlockPos.MutableBlockPos()

        val world = level

        for (i in 0 until total) {
            bounds.randomBlockPos(pos, random)

            val minecart = Minecart(EntityType.MINECART, world)
            minecart.setPosRaw(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
            minecart.isInvulnerable = true

            world.addFreshEntity(minecart)
            minecartEntities.add(minecart)

            if (i >= count) {
                createDecoyEntity(world, minecart)
            }
        }
    }

    private fun createDecoyEntity(world: ServerLevel, minecart: Minecart) {
        val frog = Frog(EntityType.FROG, world)
        frog.setPos(minecart.position())

        val frogTypes = world.registryAccess().lookupOrThrow(Registries.FROG_VARIANT).asHolderIdMap()

        if (frogTypes.size() <= 0) return

        val variant = frogTypes.byId(random.nextInt(frogTypes.size()))

        if (variant != null) {
            @Suppress("UNCHECKED_CAST", "KotlinConstantConditions")
            (frog as ApVariantHolder<Holder<FrogVariant>>).`ap2$setVariant`(variant)
        }

        world.addFreshEntity(frog)
        frog.startRiding(minecart)
    }

    private fun markFreeMinecarts() {
        minecartsGlowing = true

        for (minecart in minecartEntities) {
            if (minecart.passengers.isEmpty()) {
                minecart.setGlowingTag(true)
            }
        }
    }

    private fun removeMinecarts() {
        for (minecart in minecartEntities) {
            minecart.passengers.stream()
                .filter { entity -> entity !is ServerPlayer }
                .forEach { it.discard() }

            minecart.discard()
        }

        minecartEntities.clear()
        minecartsGlowing = false
    }

    private fun eliminatePlayers() {
        val world = level
        val participants: Participants = gameHandle.participants

        val toEliminate = HashSet<ServerPlayer>()

        for (player in participants) {
            if (player.vehicle is Minecart) continue

            val x = player.x; val y = player.y; val z = player.z

            world.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 0f)
            world.sendParticles(ParticleTypes.LAVA, x, y, z, 100, 0.5, 0.5, 0.5, 0.2)

            toEliminate.add(player)
        }

        eliminateAll(toEliminate)

        if (winManager.isGameOver || participants.count() == 0) return

        val passDelay = (NEXT_SONG_DELAY - 1.seconds).coerceAtLeast(0.seconds)

        runAfter(passDelay) {
            for (player in participants) {
                ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.5f, 1f)
            }
        }

        runAfter(NEXT_SONG_DELAY) {
            nextSong()
        }
    }

    private fun tickParticle() {
        val box = particleBox ?: return
        if (songHandle == null) return

        val world = level

        repeat(PARTICLE_AMOUNT) {
            val pos: Vec3 = box.randomPos(random)
            world.sendParticles(ParticleTypes.NOTE, pos.x(), pos.y(), pos.z(), 10, 3.0, 2.0, 3.0, 1.0)
        }
    }

    override val maxDuration: Duration
        get() = if (DEBUG_INFINITE_SONGS) 0.seconds else super.maxDuration

    @Synchronized
    private fun skipSong() {
        if (!ready.get()) return

        songHandle?.stop()
        songHandle = null

        taskHandles.forEach { it.cancel() }

        timer?.stop()

        removeMinecarts()
        nextSong()
    }
}

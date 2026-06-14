package work.lclpnet.ap2.game.minefield

import com.mojang.math.Transformation
import net.minecraft.ChatFormatting.*
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.Team
import org.joml.Matrix4f
import work.lclpnet.ap2.api.stats.CommonStats.DistanceMoved
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.*
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.ap2.ext.mc.setBlocks
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.data.OrderedDataContainer
import work.lclpnet.ap2.game.util.addTimer
import work.lclpnet.ap2.game.util.useDataContainer
import work.lclpnet.ap2.game.util.useFFAStats
import work.lclpnet.ap2.game.util.useTaskDisplay
import work.lclpnet.ap2.impl.util.Fireworks
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.handler.Visibility
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler
import work.lclpnet.ap2.impl.util.handler.VisibilityManager
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.dynamic_entities.PlayerSpecificDynamicEntity
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.access.entity.ServerPlayerAccess
import work.lclpnet.kibu.hook.level.PressurePlateCallback
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import work.lclpnet.kibu.translate.text.LocalizedFormat
import java.util.*
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration.Companion.seconds

val END_TIME = 15.seconds
const val DEBUG_PRESSURE_PLATE_POSITIONS = false

val Exploded = Stat("exploded", 0, higherIsBetter = false)

class MinefieldInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    val spawnShape: BlockShape,
    val goalShape: BlockShape,
    val spawnYaw: Float,
    val goalDistance: Double,
) : FFAGameInstance(gameHandle, level, map) {

    override val data = useDataContainer(::OrderedDataContainer)
    private val stats = useFFAStats(winManager, listOf(
        Exploded, DistanceMoved
    ))
    val inGoal = mutableSetOf<UUID>()
    val entries = mutableMapOf<UUID, Entry>()
    lateinit var taskBar: TranslatedBossBar
    lateinit var dynamicEntityManager: DynamicEntityManager
    lateinit var visibility: VisibilityHandler
    var gameEnd = -1

    override fun prepare() {
        for (player in players()) {
            player.teleport(spawnShape.randomPos(Random.asJavaRandom()))
        }

        taskBar = useTaskDisplay()

        setupTeam()

        dynamicEntityManager = DynamicEntityManager(level)
        dynamicEntityManager.init(gameHandle.scheduler, gameHandle.hooks)
    }

    fun setupTeam() {
        val scoreboardManager = gameHandle.scoreboardManager
        val team = scoreboardManager.createTeam("team")
        team.collisionRule = Team.CollisionRule.NEVER
        scoreboardManager.joinTeam(players(), team)

        val visibilityManager = VisibilityManager(team, Visibility.PARTIALLY_VISIBLE)
        visibility = VisibilityHandler(visibilityManager, gameHandle.translations, gameHandle.participants)

        visibility.init(hooks)

        visibility.giveItems()
    }

    override fun go() {
        level.setBlocks(readShape("spawn-gate"), Blocks.AIR)

        interval(1) {
            for (player in players()) {
                if (player.isSpectator) continue

                entry(player).update(player)

                if (goalShape.contains(player.position())) {
                    onReachGoal(player)
                }
            }
        }

        trackDistanceMoved(stats)

        PressurePlateCallback.HOOK.registerWith(hooks) { _, pos, entity ->
            if (entity is ServerPlayer && players().isParticipating(entity)) {
                onStepOnMine(entity, pos)
            }

            true
        }

        gameHandle.protect {
            ProtectionTypes.ALLOW_DAMAGE.allow(it) { entity, source ->
                entity is ServerPlayer && players().isParticipating(entity) && source.isOf(DamageTypes.MAGIC)
            }
        }

        val waterPoison = map.properties.optBoolean("water-poison", false)

        if (waterPoison) {
            interval(1) {
                for (player in players()) {
                    if (player.isInWater) {
                        player.addEffect(MobEffectInstance(MobEffects.POISON, Ticks.seconds(8)))
                    }
                }
            }
        }
    }

    fun entry(player: ServerPlayer): Entry = entries.computeIfAbsent(player.uuid) { Entry() }

    fun onReachGoal(player: ServerPlayer) {
        if (!inGoal.add(player.uuid) || !players().isParticipating(player)) return

        data.add(player)
        entry(player).done()

        Fireworks.spawnGoalFirework(player)

        if (inGoal.size >= players().count()) {
            winManager.complete()
            return
        }

        if (gameEnd == -1) {
            translate(
                "game.ap2.minefield.goal",
                styled(player.scoreboardName, YELLOW),
                styled(END_TIME, YELLOW)
            ).formatted(GREEN).sendTo(allPlayers())

            gameEnd = END_TIME.inWholeSeconds.toInt()

            addTimer(taskBar, END_TIME).then {
                gradePlayers()
                winManager.complete()
            }
        }
    }

    fun gradePlayers() {
        class Grade(val player: ServerPlayer, val distance: Double)

        players().stream()
            .filter { !inGoal.contains(it.uuid) }
            .map { Grade(it, entry(it).bestDist) }
            .sorted(Comparator.comparingDouble { it.distance })
            .forEachOrdered {
                val detail = translate("ap2.score.blocks_away", LocalizedFormat.format("%.1f", it.distance))
                data.add(it.player, detail)
            }
    }

    fun onStepOnMine(player: ServerPlayer, pos: BlockPos) {
        if (winManager.gameOver || player.isSpectator || inGoal.contains(player.uuid)) return

        level.setBlock(pos, Blocks.AIR)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.EXPLOSION, 1, 0.0, 0.0, 0.0, 0.0)
        SoundHelper.playSoundAt(player, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.5f, 1.2f)

        entry(player).checkUpdateMarker(player)

        translate("game.ap2.minefield.stepped_on_mine").formatted(RED).sendTo(player, true)

        player.setGameMode(GameType.SPECTATOR)

        stats.increment(player, Exploded)

        timeout(20) {
            player.teleport(spawnShape.randomPos(Random.asJavaRandom()), spawnYaw)
            gameHandle.playerUtil.resetPlayer(player)
            visibility.giveItem(player)
        }
    }

    inner class Entry {
        var pos: Vec3? = null
        var bestDist = Double.MAX_VALUE
        var marker: PlayerSpecificDynamicEntity<Display.BlockDisplay>? = null
        var label: PlayerSpecificDynamicEntity<Display.TextDisplay>? = null
        var markerDist = Double.MAX_VALUE

        fun update(player: ServerPlayer) {
            val pos = player.position()
            val dist = sqrt(goalShape.bounds().squaredDistanceTo(pos)).coerceAtMost(goalDistance)

            if (dist >= this.bestDist) return

            this.bestDist = dist
            this.pos = pos

            if (marker != null) {
                ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 0.3f, 2f)
                translate("game.ap2.minefield.new_personal_best").formatted(GREEN).sendTo(player, true)
            }

            removeMarker()
        }

        fun checkUpdateMarker(player: ServerPlayer) {
            if (bestDist >= markerDist) return

            updateMarker(player)
        }

        fun updateMarker(player: ServerPlayer) {
            val pos = pos ?: return

            if (marker == null || label == null) {
                createMarker(player)
            }

            marker!!.entity.setPos(pos)
            label!!.entity.setPos(pos.add(0.0, 0.6, 0.0))

            markerDist = bestDist
        }

        fun createMarker(player: ServerPlayer) {
            val marker = Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level)
            marker.setTransformation(Transformation(Matrix4f().scale(0.5f).translate(-0.5f, 0f, -0.5f)))
            marker.setGlowingTag(true)
            marker.glowColorOverride = DyeColor.LIME.textureDiffuseColor
            marker.blockState = Blocks.LIME_TERRACOTTA.defaultBlockState()

            val label = Display.TextDisplay(EntityType.TEXT_DISPLAY, level)
            label.setTransformation(Transformation(Matrix4f().scale(0.5f)))
            label.billboardConstraints = Display.BillboardConstraints.CENTER
            label.backgroundColor = 0

            val dist = max(0.0, goalDistance - bestDist)

            label.text = translate(
                "game.ap2.minefield.personal_best",
                styled(LocalizedFormat.format("%.2f", dist), YELLOW)
            ).formatted(GREEN).translateFor(player)

            this.marker = PlayerSpecificDynamicEntity(marker, player.uuid)
            this.label = PlayerSpecificDynamicEntity(label, player.uuid)

            dynamicEntityManager.add(this.marker)
            dynamicEntityManager.add(this.label)
        }

        fun done() {
            bestDist = 0.0

            removeMarker()
        }

        fun removeMarker() {
            if (marker != null) {
                dynamicEntityManager.remove(marker)
                marker = null
            }

            if (label != null) {
                dynamicEntityManager.remove(label)
                label = null
            }
        }
    }
}

package work.lclpnet.ap2.game.minefield

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.entity.EntityType
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.particle.ParticleTypes
import net.minecraft.scoreboard.AbstractTeam
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.DyeColor
import net.minecraft.util.Formatting.*
import net.minecraft.util.math.AffineTransformation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import net.minecraft.world.GameRules
import org.joml.Matrix4f
import org.json.JSONArray
import work.lclpnet.ap2.*
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrapFunction
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.Fireworks
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.handler.Visibility
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler
import work.lclpnet.ap2.impl.util.handler.VisibilityManager
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager
import work.lclpnet.gaco.dynamic_entities.PlayerSpecificDynamicEntity
import work.lclpnet.kibu.hook.world.PressurePlateCallback
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import work.lclpnet.kibu.translate.text.FormatWrapper.styled
import work.lclpnet.kibu.translate.text.LocalizedFormat
import work.lclpnet.kibu.util.BlockStateUtils
import work.lclpnet.kibu.util.math.Matrix3i
import work.lclpnet.lobby.game.api.prot.scope.EntityDamageSourceScope
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes
import work.lclpnet.lobby.game.map.GameMap
import java.util.*
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.asJavaRandom

const val END_TIME_SECONDS = 15
const val DEBUG_PRESSURE_PLATE_POSITIONS = false

class MinefieldInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle), MapBootstrapFunction {
    
    private val data = OrderedDataContainer(PlayerRef::create)

    val inGoal = mutableSetOf<UUID>()
    var gameEnd = -1
    var taskBar: TranslatedBossBar? = null
    var spawnShape: BlockShape? = null
    var goalShape: BlockShape? = null
    var goalDistance: Double? = null
    var spawnYaw = 0f
    val entries = mutableMapOf<UUID, Entry>()
    var dynamicEntityManager: DynamicEntityManager? = null
    var visibility: VisibilityHandler? = null
    
    override fun getData() = data

    override fun bootstrapWorld(world: ServerWorld, map: GameMap) {
        val scanPositions = map.properties.getJSONArray("scan-positions")
        val mineDensity = map.properties.optNumber("mine-density", 0.55f).toFloat()
        val scanShape = readShape("scan-shape")
        val startAnchorPos = MapUtil.readVec3d(map.properties.getJSONArray("start-anchor-pos"))

        spawnShape = readShape("spawn-shape")
        goalShape = readShape("goal-shape")
        spawnYaw = MapUtil.readAngle(map.properties.optNumber("spawn-yaw", 0))
        goalDistance = sqrt(goalShape!!.bounds().squaredDistanceTo(startAnchorPos))

        val defaultPressurePlates = JSONArray()
        defaultPressurePlates.put(BlockStateUtils.stringify(Blocks.STONE_PRESSURE_PLATE.defaultState))
        val pressurePlatesJson = map.properties.optJSONArray("pressure-plates", defaultPressurePlates)
        val pressurePlates = mutableSetOf<BlockState>()
        MapUtil.readBlockStates(pressurePlatesJson, pressurePlates, gameHandle.logger)

        val predicate = BlockPredicate.and({
            scanShape.contains(it) && !spawnShape!!.contains(it) && !goalShape!!.contains(it)
                    && world.getBlockState(it).getCollisionShape(world, it, ShapeContext.absent()).isEmpty
        }, WalkableBlockPredicate(world))

        val scanner =  BfsWorldScanner(SimpleAdjacentBlocks(predicate, 1))
        val debugVoxelShape = if (DEBUG_PRESSURE_PLATE_POSITIONS) StructureMask.createEmpty(scanShape.bounds()) else null
        val minPos = scanShape.bounds().min()

        for (elem in scanPositions) {
            if (elem !is JSONArray) continue

            val start = MapUtil.readBlockPos(elem)

            scanner.scan(start).forEach {
                @Suppress("KotlinConstantConditions")
                debugVoxelShape?.setVoxelAt(it.x - minPos.x, it.y - minPos.y, it.z - minPos.z, true)

                if (Random.nextFloat() > mineDensity) return@forEach

                world.setBlockState(it, pressurePlates.random(), Block.FORCE_STATE or Block.SKIP_DROPS)
            }
        }

        @Suppress("KotlinConstantConditions")
        if (debugVoxelShape != null) {
            val boxes = debugVoxelShape.greedyMeshing().generateBoxes()

            commons(map, world).debugController().visualizeBoxes(
                boxes,
                minPos,
                Matrix3i.IDENTITY,
                Blocks.BLUE_STAINED_GLASS.defaultState
            )
        }

        commons().gameRuleBuilder()
            .set(GameRules.NATURAL_REGENERATION, false)
    }

    override fun prepare() {
        for (player in players()) {
            player.teleport(spawnShape!!.randomPos(Random.asJavaRandom()))
        }

        taskBar = useTaskDisplay()

        setupTeam()

        dynamicEntityManager = DynamicEntityManager(world)
        dynamicEntityManager!!.init(gameHandle.gameScheduler, gameHandle.hooks)
    }

    fun setupTeam() {
        val scoreboardManager = gameHandle.getScoreboardManager()
        val team = scoreboardManager.createTeam("team")
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER)
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team)

        val visibilityManager = VisibilityManager(team, Visibility.VISIBLE)
        visibility = VisibilityHandler(visibilityManager, gameHandle.translations, gameHandle.participants)

        visibility!!.init(gameHandle.getHooks())

        visibility!!.giveItems()
    }

    override fun ready() {
        world.setBlocks(readShape("spawn-gate"), Blocks.AIR)

        interval(1) {
            for (player in players()) {
                if (player.isSpectator) continue

                entry(player).update(player)

                if (goalShape!!.contains(player.pos)) {
                    onReachGoal(player)
                }
            }
        }

        gameHandle.hooks.registerHook(PressurePlateCallback.HOOK, PressurePlateCallback { _, pos, entity ->
            if (entity is ServerPlayerEntity && players().isParticipating(entity)) {
                onStepOnMine(entity, pos)
            }

            return@PressurePlateCallback true
        })

        gameHandle.protect {
            it.allow(ProtectionTypes.ALLOW_DAMAGE, EntityDamageSourceScope { entity, source ->
                entity is ServerPlayerEntity && players().isParticipating(entity) && source.isOf(DamageTypes.MAGIC)
            })
        }

        val waterPoison = map.properties.optBoolean("water-poison", false)

        if (waterPoison) {
            interval(1) {
                for (player in players()) {
                    if (player.isTouchingWater) {
                        player.addStatusEffect(StatusEffectInstance(StatusEffects.POISON, Ticks.seconds(8)))
                    }
                }
            }
        }
    }

    fun entry(player: ServerPlayerEntity): Entry = entries.computeIfAbsent(player.uuid) { Entry() }

    fun onReachGoal(player: ServerPlayerEntity) {
        if (!inGoal.add(player.uuid) || !players().isParticipating(player)) return

        data.add(player)
        entry(player).done()

        Fireworks.spawnGoalFirework(player)

        if (inGoal.size >= gameHandle.getParticipants().count()) {
            winManager.complete()
            return
        }

        if (gameEnd == -1) {
            translate(
                "game.ap2.minefield.goal",
                styled(player.nameForScoreboard, YELLOW),
                styled(END_TIME_SECONDS, YELLOW)
            ).formatted(GREEN).sendTo(allPlayers())

            gameEnd = Ticks.seconds(END_TIME_SECONDS)

            commons().addTimer(taskBar, END_TIME_SECONDS).then {
                gradePlayers()
                winManager.complete()
            }
        }
    }

    fun gradePlayers() {
        class Grade(val player: ServerPlayerEntity, val distance: Double)

        players().stream()
            .filter { !inGoal.contains(it.uuid) }
            .map { Grade(it, entry(it).bestDist) }
            .sorted(Comparator.comparingDouble { it.distance })
            .forEachOrdered {
                val detail = translate("ap2.score.blocks_away", LocalizedFormat.format("%.1f", it.distance))
                data.add(it.player, detail)
            }
    }

    fun MinefieldInstance.onStepOnMine(player: ServerPlayerEntity, pos: BlockPos) {
        if (winManager.isGameOver || player.isSpectator || inGoal.contains(player.uuid)) return

        world.setBlock(pos, Blocks.AIR)
        ParticleHelper.spawnParticleAt(player, ParticleTypes.EXPLOSION, 1, 0.0, 0.0, 0.0, 0.0)
        SoundHelper.playSoundAt(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.5f, 1.2f)

        entry(player).checkUpdateMarker(player)

        translate("game.ap2.minefield.stepped_on_mine").formatted(RED).sendTo(player, true)

        player.changeGameMode(GameMode.SPECTATOR)

        timeout(20) {
            player.teleport(spawnShape!!.randomPos(Random.asJavaRandom()), spawnYaw)
            gameHandle.playerUtil.resetPlayer(player)
            visibility!!.giveItem(player)
        }
    }

    inner class Entry {
        var pos: Vec3d? = null
        var bestDist = Double.MAX_VALUE
        var marker: PlayerSpecificDynamicEntity<DisplayEntity.BlockDisplayEntity>? = null
        var label: PlayerSpecificDynamicEntity<DisplayEntity.TextDisplayEntity>? = null
        var markerDist = Double.MAX_VALUE

        fun update(player: ServerPlayerEntity) {
            val pos = player.pos
            val dist = sqrt(goalShape!!.bounds().squaredDistanceTo(pos)).coerceAtMost(goalDistance!!)

            if (dist >= this.bestDist) return

            this.bestDist = dist
            this.pos = pos

            if (marker != null) {
                player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.NEUTRAL, 0.3f, 2f)
                translate("game.ap2.minefield.new_personal_best").formatted(GREEN).sendTo(player, true)
            }

            removeMarker()
        }

        fun checkUpdateMarker(player: ServerPlayerEntity) {
            if (bestDist >= markerDist) return

            updateMarker(player)
        }

        fun updateMarker(player: ServerPlayerEntity) {
            if (pos == null) return

            if (marker == null || label == null) {
                createMarker(player)
            }

            marker!!.entity.setPosition(pos)
            label!!.entity.setPosition(pos!!.add(0.0, 0.6, 0.0))

            markerDist = bestDist
        }

        fun createMarker(player: ServerPlayerEntity) {
            val marker = DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world)
            marker.setTransformation(AffineTransformation(Matrix4f().scale(0.5f).translate(-0.5f, 0f, -0.5f)))
            marker.isGlowing = true
            marker.glowColorOverride = DyeColor.LIME.entityColor
            marker.blockState = Blocks.LIME_TERRACOTTA.defaultState

            val label = DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world)
            label.setTransformation(AffineTransformation(Matrix4f().scale(0.5f)))
            label.billboardMode = DisplayEntity.BillboardMode.CENTER
            label.background = 0

            val dist = max(0.0, goalDistance!! - bestDist)

            label.text = translate(
                "game.ap2.minefield.personal_best",
                styled(LocalizedFormat.format("%.2f", dist), YELLOW)
            ).formatted(GREEN).translateFor(player)

            this.marker = PlayerSpecificDynamicEntity(marker, player.uuid)
            this.label = PlayerSpecificDynamicEntity(label, player.uuid)

            dynamicEntityManager!!.add(this.marker)
            dynamicEntityManager!!.add(this.label)
        }

        fun done() {
            bestDist = 0.0

            removeMarker()
        }

        fun removeMarker() {
            if (marker != null) {
                dynamicEntityManager!!.remove(marker)
                marker = null
            }

            if (label != null) {
                dynamicEntityManager!!.remove(label)
                label = null
            }
        }
    }
}

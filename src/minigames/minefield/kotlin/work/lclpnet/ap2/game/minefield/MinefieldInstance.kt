package work.lclpnet.ap2.game.minefield

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting.*
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameMode
import org.json.JSONArray
import work.lclpnet.ap2.*
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrapFunction
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.ap2.impl.ds.StructureMask
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.Fireworks
import work.lclpnet.ap2.impl.util.ParticleHelper
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
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
    var spawnYaw = 0f
    
    override fun getData() = data

    init {
        useNoHealing()
    }

    override fun bootstrapWorld(world: ServerWorld, map: GameMap) {
        val scanPositions = map.properties.getJSONArray("scan-positions")
        val mineDensity = map.properties.optNumber("mine-density", 0.55f).toFloat()
        val scanShape = readShape("scan-shape")
        val spawnShape = readShape("spawn-shape")
        val goalShape = readShape("goal-shape")

        spawnYaw = MapUtil.readAngle(map.properties.optNumber("spawn-yaw", 0))

        val defaultPressurePlates = JSONArray()
        defaultPressurePlates.put(BlockStateUtils.stringify(Blocks.STONE_PRESSURE_PLATE.defaultState))
        val pressurePlatesJson = map.properties.optJSONArray("pressure-plates", defaultPressurePlates)
        val pressurePlates = mutableSetOf<BlockState>()
        MapUtil.readBlockStates(pressurePlatesJson, pressurePlates, gameHandle.logger)

        val predicate = BlockPredicate.and({
            scanShape.contains(it) && !spawnShape.contains(it) && !goalShape.contains(it)
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
    }

    override fun prepare() {
        spawnShape = readShape("spawn-shape")

        for (player in players()) {
            player.teleport(spawnShape!!.randomPos(Random.asJavaRandom()))
        }

        taskBar = useTaskDisplay()
    }

    override fun ready() {
        world.setBlocks(readShape("spawn-gate"), Blocks.AIR)

        goalShape = readShape("goal-shape")

        interval(1) {
            for (player in players()) {
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

    fun onReachGoal(player: ServerPlayerEntity) {
        if (!inGoal.add(player.uuid)) return

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
            .filter { !inGoal.contains(it.uuid) && !it.isSpectator }
            .map { Grade(it, sqrt(goalShape!!.bounds().squaredDistanceTo(it.pos))) }
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

        translate("game.ap2.minefield.stepped_on_mine").formatted(RED).sendTo(player, true)

        player.changeGameMode(GameMode.SPECTATOR)

        timeout(20) {
            player.teleport(spawnShape!!.randomPos(Random.asJavaRandom()), spawnYaw)
            gameHandle.playerUtil.resetPlayer(player)
        }
    }
}

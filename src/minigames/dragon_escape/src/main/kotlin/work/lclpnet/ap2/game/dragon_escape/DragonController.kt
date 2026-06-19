package work.lclpnet.ap2.game.dragon_escape

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LevelEvent
import work.lclpnet.ap2.core.type.ApEnderDragon
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.math.SplinePath
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import java.util.*
import kotlin.math.max
import kotlin.math.min

private const val MIN_SPEED_BPS = 1.6
private const val MAX_SPEED_BPS = 16.0
private const val ACCELERATION_BPS2 = 3.0
private const val ACCELERATION_DISTANCE = 30.0

class DragonController(
    private val path: SplinePath,
    private val world: ServerLevel,
    private val random: Random,
    private val canDestroy: (BlockPos) -> Boolean,
    private val remainingPlayers: () -> Iterator<ServerPlayer>
) {
    private var dragon: EnderDragon? = null
    var dragonProgress: Double = 0.0
        private set
    private var speedBps: Double = 0.0

    fun spawnDragon() {
        val dragon = EnderDragon(EntityTypes.ENDER_DRAGON, world)

        @Suppress("KotlinConstantConditions")
        (dragon as ApEnderDragon).`ap2$setManuallyManaged`()

        setProgress(dragon, 0.0)
        world.addFreshEntity(dragon)

        this.dragon = dragon
    }

    fun init(scheduler: TaskScheduler) {
        scheduler.interval(::tick, 1)
    }

    fun startMoving(scheduler: TaskScheduler) {
        scheduler.interval(::tickMovement, 1)
    }

    private fun tick() {
        val dragon = dragon ?: return

        destroyBlocks(BlockBox.of(dragon.boundingBox
            .inflate(0.0, 2.0, 0.0)
            .move(0.0, -2.0, 0.0)))
    }

    private fun tickMovement() {
        val dragon = dragon ?: return

        val dist = getDistanceToLastPlayer()
        val brakeDist = (speedBps * speedBps - MIN_SPEED_BPS * MIN_SPEED_BPS) / (2 * ACCELERATION_BPS2)

        if (dist > ACCELERATION_DISTANCE + brakeDist) {
            speedBps = min(speedBps + ACCELERATION_BPS2 / 20.0, MAX_SPEED_BPS)
        } else {
            speedBps = max(speedBps - ACCELERATION_BPS2 / 20.0, MIN_SPEED_BPS)
        }

        val stepPerTick = speedBps / 20.0 / path.length

        dragonProgress = (dragonProgress + stepPerTick).coerceIn(0.0, 1.0)

        setProgress(dragon, dragonProgress)
    }

    private fun getDistanceToLastPlayer(): Double {
        val origin = dragonProgress
        var minDist = Double.POSITIVE_INFINITY

        for (player in remainingPlayers()) {
            val progress = path.getProgress(player.position())
            val dist = progress - origin

            if (dist in 0.0..<minDist) {
                minDist = dist
            }
        }

        if (minDist.isInfinite()) return 0.0

        return minDist * path.length
    }

    private fun setProgress(dragon: EnderDragon, s: Double) {
        val pos = path.samplePosition(s)
        dragon.setPos(pos)

        val dir = path.sampleDirection(s).normalize().scale(-1.0)
        dragon.yRot = MathUtil.yaw(dir)
        dragon.xRot = MathUtil.pitch(dir)
    }

    fun dragon(): EnderDragon? = dragon

    private fun destroyBlocks(box: BlockBox) {
        var destroyed = 0

        for (pos in box) {
            if (world.getBlockState(pos).isAir || !canDestroy(pos)) continue

            if (world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                destroyed++
            }
        }

        if (destroyed <= 0) return

        val pos = BlockPos.MutableBlockPos()
        val amount = max(1, destroyed / 20)

        repeat(amount) {
            box.randomBlockPos(pos, random)
            world.levelEvent(LevelEvent.PARTICLES_DRAGON_BLOCK_BREAK, pos, 0)
        }
    }
}

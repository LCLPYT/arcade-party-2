package work.lclpnet.ap2.game.knockout

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import org.json.JSONObject
import work.lclpnet.ap2.game.knockout.util.DistanceIterator
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val DEFAULT_DELAY_SECONDS = 90
private val DEFAULT_PERIOD_TICKS = Ticks.seconds(1)

internal fun buildDistancesArray(radius: Int): Array<ShortArray> {
    val distances = Array(2 * radius + 1) { ShortArray(2 * radius + 1) }

    for (x in -radius..radius) {
        val xSquared = x * x
        val ix = x + radius

        for (z in -radius..radius) {
            distances[ix][z + radius] = sqrt((xSquared + z * z).toDouble()).roundToInt().toShort()
        }
    }

    return distances
}

class KnockoutWorldCrumble(private val world: ServerLevel, private val map: GameMap) {

    private var distances: Array<ShortArray>? = null
    private var centerX = 0
    private var centerZ = 0
    private var radius = 0
    private var currentDistance: Short = -1
    private var warn = true
    var delaySeconds = DEFAULT_DELAY_SECONDS
        private set
    private var periodTicks = DEFAULT_PERIOD_TICKS

    fun init() {
        val crumble: JSONObject = map.requireProperty("crumble")

        if (!crumble.has("radius")) {
            throw IllegalStateException("Property radius is undefined")
        }

        radius = abs(crumble.getNumber("radius").toInt())

        if (crumble.has("center")) {
            val center = MapUtil.readVec2i(crumble.getJSONArray("center"))
            centerX = center.x()
            centerZ = center.z()
        } else {
            centerX = 0
            centerZ = 0
        }

        if (crumble.has("delay")) {
            delaySeconds = maxOf(0, crumble.getNumber("delay").toInt())
        }

        if (crumble.has("period")) {
            periodTicks = maxOf(1, crumble.getNumber("period").toInt())
        }

        distances = buildDistancesArray(radius)
        currentDistance = findFurthestDistance(distances!!)
    }

    private fun findFurthestDistance(distances: Array<ShortArray>): Short {
        val maxDistance = distances[0][0].toInt()

        for (r in maxDistance downTo 0) {
            for (pos in iterateBlocks(r)) {
                if (world.isEmptyBlock(pos)) continue
                return sqrt(
                    (centerX - pos.x).toDouble().pow(2) + (centerZ - pos.z).toDouble().pow(2)
                ).roundToInt().toShort()
            }
        }

        return -1
    }

    fun start(scheduler: TaskScheduler) {
        scheduler.interval(periodTicks, this::tick)
    }

    private fun tick(task: RunningTask) {
        if (currentDistance < 0) {
            task.cancel()
            return
        }

        if (warn) {
            warn = false
            markBlocks()
            return
        }

        removeBlocks()
        currentDistance--
        warn = true
    }

    private fun markBlocks() {
        val markerState = Blocks.DYED_TERRACOTTA.red.defaultBlockState()

        for (pos in iterateBlocks(currentDistance.toInt())) {
            val state = world.getBlockState(pos)
            if (!state.isCollisionShapeFullBlock(world, pos)) continue
            world.setBlock(pos, markerState, Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_CLIENTS)
        }
    }

    private fun removeBlocks() {
        val air = Blocks.AIR.defaultBlockState()

        for (pos in iterateBlocks(currentDistance.toInt())) {
            val state = world.getBlockState(pos)
            if (state.isAir) continue
            world.setBlock(pos, air, Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_CLIENTS)
        }
    }

    private fun iterateBlocks(targetDistance: Int): Iterable<BlockPos> {
        return Iterable { DistanceIterator(radius, centerX, centerZ, world.minY, world.maxY, distances!!, targetDistance) }
    }
}

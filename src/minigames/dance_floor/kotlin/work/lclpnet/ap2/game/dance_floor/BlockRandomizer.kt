package work.lclpnet.ap2.game.dance_floor

import net.minecraft.server.world.ServerWorld
import net.minecraft.util.DyeColor
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import work.lclpnet.ap2.impl.ds.WeightedList
import work.lclpnet.ap2.impl.util.BlockHelper
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.ap2.setBlock
import java.util.Objects.hash
import kotlin.math.*
import kotlin.random.Random
import kotlin.random.asJavaRandom

interface Pattern {
    val minColors: Int
        get() = 12

    val maxColors: Int
        get() = 12

    fun init() {}

    fun group(pos: BlockPos): Int
}

class BlockRandomizer(val floorShape: BlockShape, val world: ServerWorld) {

    val patterns = WeightedList<Pattern>().apply {
        add(Uniform(), 0.9f)
        add(CheckerBoard(), 1f)
        add(StripesX(), 0.4f)
        add(StripesZ(), 0.4f)
        add(Circles(), 0.5f)
        add(Taxicab(), 0.5f)
        add(Chebyshev(), 1f)
        add(Parabola(), 1f)
        add(Diagonal(), 0.65f)
        add(Angled(), 0.3f)
        add(Voronoi(), 0.5f)
        add(Honeycomb(), 1f)
        add(Spirals(), 0.5f)
        add(PerlinNoise(), 1f)
        add(Mandelbrot(), 0.3f)
    }

    val existingColors = mutableListOf<DyeColor>()

    fun randomizeBlocks() {
        existingColors.clear()

        val pattern = patterns.getRandomElement(Random.asJavaRandom())!!.apply { init() }
        val baseColors = listOf(
            DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE, DyeColor.YELLOW, DyeColor.LIME,
            DyeColor.PINK, DyeColor.PURPLE, DyeColor.BLUE, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK
        )

        val options = baseColors.shuffled().take(Random.nextInt(pattern.minColors, pattern.maxColors + 1))
        val pool = mutableListOf<DyeColor>()

        fun randomColor(): DyeColor {
            if (pool.isEmpty()) {
                pool.addAll(options)
                pool.shuffle()
            }

            return pool.removeFirst()
        }

        val groups = mutableMapOf<Int, MutableList<BlockPos>>()

        for (pos in floorShape) {
            val positions = groups.computeIfAbsent(pattern.group(pos)) { mutableListOf() }
            positions.add(pos.toImmutable())
        }

        val colorGroups = mutableMapOf<DyeColor, MutableList<BlockPos>>()

        for ((_, positions) in groups) {
            val color = randomColor()
            colorGroups.computeIfAbsent(color) { mutableListOf() }.addAll(positions)
        }

        val totalPositions: Int = colorGroups.values.sumOf { it.size }
        val mergeThreshold = 0.05f

        val sortedGroups = colorGroups.entries
            .map { it.key to it.value }
            .sortedBy { it.second.size }
            .toMutableList()

        while (sortedGroups.size >= 2) {
            val (_, group) = sortedGroups[0]
            val ratio = group.size.toFloat() / totalPositions.toFloat()

            if (ratio >= mergeThreshold || sortedGroups.size <= 1) break

            val (_, nextGroup) = sortedGroups[1]
            nextGroup.addAll(group)

            sortedGroups.removeAt(0)

            sortedGroups.sortBy { it.second.size }
        }

        for ((color, positions) in sortedGroups) {
            if (positions.isEmpty()) continue

            existingColors.add(color)

            for (pos in positions) {
                world.setBlock(pos, BlockHelper.getWool(color))
            }
        }
    }

    class Uniform(override val minColors: Int = 10, override val maxColors: Int = 12) : Pattern {
        override fun group(pos: BlockPos): Int = Random.nextInt(16)
    }

    class CheckerBoard(override val minColors: Int = 6, override val maxColors: Int = 7) : Pattern {
        override fun group(pos: BlockPos): Int = hash(floor( pos.x / 3f), floor(pos.z / 3f))
    }

    class StripesX(override val minColors: Int = 6, override val maxColors: Int = 9) : Pattern {
        override fun group(pos: BlockPos) = pos.x
    }

    class StripesZ(override val minColors: Int = 6, override val maxColors: Int = 9) : Pattern {
        override fun group(pos: BlockPos) = pos.z
    }

    inner class Circles(override val minColors: Int = 6, override val maxColors: Int = 8) : Pattern {
        override fun group(pos: BlockPos): Int = sqrt(pos.getSquaredDistance(floorShape.center())).roundToInt()
    }

    inner class Taxicab(override val minColors: Int = 7, override val maxColors: Int = 9) : Pattern {
        override fun group(pos: BlockPos): Int = pos.getManhattanDistance(floorShape.center())
    }

    inner class Chebyshev(override val minColors: Int = 6, override val maxColors: Int = 9) : Pattern {
        override fun group(pos: BlockPos): Int = pos.getChebyshevDistance(floorShape.center())
    }

    inner class Parabola(override val minColors: Int = 10, override val maxColors: Int = 12) : Pattern {
        override fun group(pos: BlockPos): Int {
            val c = floorShape.center().toCenterPos()
            return pos.getSquaredDistanceFromCenter(c.x, c.y, c.z).roundToInt()
        }
    }

    inner class Diagonal(override val minColors: Int = 6, override val maxColors: Int = 8) : Pattern {
        override fun group(pos: BlockPos): Int = pos.getManhattanDistance(floorShape.min())
    }

    inner class Angled : Pattern {
        var subdivisions = 5
        var refAngle = 0.0

        override fun init() {
            subdivisions = Random.nextInt(5, 12)
            refAngle = Random.nextDouble() * PI * 2
        }

        override fun group(pos: BlockPos): Int {
            val dir = pos.toCenterPos().subtract(Vec3d.ofCenter(floorShape.center())).normalize()
            val angleY = MathUtil.angleY(dir.x, dir.z)
            val angle = (angleY + refAngle + 2 * PI) % (2 * PI)

            return (angle / (2 * PI) * subdivisions).toInt()
        }
    }

    inner class Voronoi(override val minColors: Int = 6, override val maxColors: Int = 8) : Pattern {
        private var seeds: List<Vec3d> = emptyList()

        override fun init() {
            val count = Random.nextInt(65, 80)
            seeds = List(count) { floorShape.randomPos(Random.asJavaRandom()) }
        }

        override fun group(pos: BlockPos): Int {
            var closestIndex = 0
            var closestDist = Double.MAX_VALUE

            for ((i, seed) in seeds.withIndex()) {
                val dx = pos.x + 0.5 - seed.x
                val dz = pos.z + 0.5 - seed.z
                val dist = dx * dx + dz * dz

                if (dist < closestDist) {
                    closestDist = dist
                    closestIndex = i
                }
            }

            return closestIndex
        }
    }

    class Honeycomb(override val minColors: Int = 7, override val maxColors: Int = 10) : Pattern {
        private var size = 4

        override fun init() {
            size = Random.nextInt(3, 7)
        }

        override fun group(pos: BlockPos): Int {
            val x = pos.x.toDouble() / size
            val z = pos.z.toDouble() / size * (2.0 / sqrt(3.0))

            // Cube coordinates for hex
            val q = x - z / 2
            val s = -q - z

            // Round to nearest hex
            val rq = round(q)
            val rr = round(z)
            val rs = round(s)

            val dq = abs(rq - q)
            val dr = abs(rr - z)
            val ds = abs(rs - s)

            var qh = rq
            var rh = rr

            if (dq > dr && dq > ds) qh = -rh - rs
            else if (dr > ds) rh = -qh - rs

            return ((qh + 1000).toInt() shl 16) xor ((rh + 1000).toInt() and 0xFFFF)
        }
    }

    inner class Spirals(override val minColors: Int = 8, override val maxColors: Int = 8) : Pattern {
        private var arms = 5
        private var tightness = 4.0

        override fun init() {
            arms = Random.nextInt(4, 7)
            tightness = Random.nextDouble(3.0, 8.0)
        }

        override fun group(pos: BlockPos): Int {
            val dx = pos.x - floorShape.center().x
            val dz = pos.z - floorShape.center().z
            val r = sqrt((dx * dx + dz * dz).toDouble())
            val angle = atan2(dz.toDouble(), dx.toDouble())

            val spiral = angle - r / tightness
            val normalized = (spiral + 2 * PI) % (2 * PI)

            return ((normalized / (2 * PI)) * arms).toInt()
        }
    }

    class PerlinNoise : Pattern {
        private var scale = 0.1

        override fun init() {
            scale = Random.nextDouble(0.05, 0.2)
        }

        override fun group(pos: BlockPos): Int {
            val nx = pos.x * scale
            val nz = pos.z * scale
            val noiseVal = perlin(nx, nz)
            val normalized = (noiseVal + 1) / 2.0

            return (normalized * 8).toInt() // 8 groups
        }

        private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
        private fun lerp(a: Double, b: Double, t: Double) = a + t * (b - a)
        private fun grad(hash: Int, x: Double, y: Double): Double {
            val h = hash and 3
            return when (h) {
                0 -> x + y
                1 -> -x + y
                2 -> x - y
                else -> -x - y
            }
        }

        private val perm = IntArray(512) { Random.nextInt(0, 256) }

        private fun perlin(x: Double, y: Double): Double {
            val xs = floor(x).toInt() and 255
            val ys = floor(y).toInt() and 255

            val xf = x - floor(x)
            val yf = y - floor(y)

            val u = fade(xf)
            val v = fade(yf)

            val aa = perm[xs + perm[ys]] and 255
            val ab = perm[xs + perm[ys + 1]] and 255
            val ba = perm[xs + 1 + perm[ys]] and 255
            val bb = perm[xs + 1 + perm[ys + 1]] and 255

            val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
            val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)

            return lerp(x1, x2, v)
        }
    }

    inner class Mandelbrot : Pattern {
        private var scale = 0.05
        private var maxIter = 40
        private var offsetX = 1.0

        override fun init() {
            scale = Random.nextDouble(0.01, 0.06)
            offsetX = Random.nextDouble(0.5, 1.5)
        }

        override fun group(pos: BlockPos): Int {
            val cx = (pos.x - floorShape.center().x) * scale - offsetX
            val cy = (pos.z - floorShape.center().z) * scale

            var x = 0.0
            var y = 0.0
            var iter = 0

            while (x * x + y * y <= 4 && iter < maxIter) {
                val xt = x * x - y * y + cx
                y = 2 * x * y + cy
                x = xt
                iter++
            }

            return iter % 8
        }
    }
}
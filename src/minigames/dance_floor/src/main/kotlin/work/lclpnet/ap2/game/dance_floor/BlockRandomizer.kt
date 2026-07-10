package work.lclpnet.ap2.game.dance_floor

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.ap2.impl.util.math.MathUtil
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.WeightedList
import java.util.Objects.hash
import kotlin.math.*
import kotlin.random.Random
import kotlin.random.asJavaRandom

private const val MAX_LAYOUT_ATTEMPTS = 8
private const val LARGE_COVERAGE = Int.MAX_VALUE / 2

interface Pattern {
    val minColors: Int
        get() = 12

    val maxColors: Int
        get() = 12

    fun init() {}

    fun group(pos: BlockPos): Int
}

class BlockRandomizer(val floorShape: BlockShape, val world: ServerLevel) {

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
        add(Hexagons(), 1f)
        add(Triangles(), 1f)
        add(EinsteinTiles(), 0.45f)
        add(Penrose(), 0.45f)
        add(HilbertCurve(), 0.45f)
    }

    val existingColors = mutableListOf<DyeColor>()

    // fixed 2d grid of the floor cells, keyed by (x, z), used for coverage evaluation
    private val cellIndex = HashMap<Long, Int>()
    private val cellX: IntArray
    private val cellZ: IntArray
    private var currentLayout: Layout? = null

    init {
        val xs = ArrayList<Int>()
        val zs = ArrayList<Int>()

        for (pos in floorShape) {
            val key = packKey(pos.x, pos.z)

            if (cellIndex.putIfAbsent(key, xs.size) == null) {
                xs.add(pos.x)
                zs.add(pos.z)
            }
        }

        cellX = xs.toIntArray()
        cellZ = zs.toIntArray()
    }

    private fun packKey(x: Int, z: Int): Long =
        (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)

    /**
     * The coverage radius of a set of cells: the maximum graph distance (8-connectivity)
     * of any floor cell to the nearest cell in the set.
     * Lower means better distributed.
     * A floor cell that cannot reach the set (disconnected floor) yields [LARGE_COVERAGE].
     */
    private fun coverageRadius(seeds: IntArray): Int {
        if (seeds.isEmpty()) return LARGE_COVERAGE

        val dist = IntArray(cellX.size) { -1 }
        val queue = ArrayDeque<Int>()

        for (s in seeds) {
            if (dist[s] == -1) {
                dist[s] = 0
                queue.add(s)
            }
        }

        var maxDist = 0

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val d = dist[cur]
            if (d > maxDist) maxDist = d

            val cx = cellX[cur]
            val cz = cellZ[cur]

            for (dx in -1..1) for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue

                val ni = cellIndex[packKey(cx + dx, cz + dz)] ?: continue

                if (dist[ni] == -1) {
                    dist[ni] = d + 1
                    queue.add(ni)
                }
            }
        }

        for (d in dist) if (d == -1) return LARGE_COVERAGE

        return maxDist
    }

    private inner class Layout(val colorPositions: Map<DyeColor, List<BlockPos>>) {
        val coverage: Map<DyeColor, Int> = colorPositions.mapValues { (_, positions) ->
            val idx = positions.mapNotNull { cellIndex[packKey(it.x, it.z)] }.toIntArray()
            coverageRadius(idx)
        }

        // best achievable coverage if the fairest color were chosen as the safe one
        val bestCoverage: Int = coverage.values.minOrNull() ?: LARGE_COVERAGE
    }

    fun randomizeBlocks(maxCoverage: Int) {
        existingColors.clear()

        // reject layouts coarser than the round can fairly handle (each attempt re-rolls a random
        // pattern), keeping the best-scoring attempt as a fallback so we never hard-fail
        var chosen: Layout? = null
        var attempts = 0

        while (attempts < MAX_LAYOUT_ATTEMPTS) {
            val layout = buildLayout()

            if (chosen == null || layout.bestCoverage < chosen.bestCoverage) {
                chosen = layout
            }

            if (layout.bestCoverage <= maxCoverage) break

            attempts++
        }

        val layout = chosen!!
        currentLayout = layout

        for ((color, positions) in layout.colorPositions) {
            if (positions.isEmpty()) continue

            existingColors.add(color)

            for (pos in positions) {
                world.setBlock(pos, Blocks.WOOL.pick(color))
            }
        }
    }

    private fun buildLayout(): Layout {
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
            positions.add(pos.immutable())
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

        return Layout(sortedGroups.toMap())
    }

    /**
     * Picks the safe color for a round given how far a player can travel in the reaction window ([reachBlocks]).
     * Prefers a random color reachable from anywhere on the floor.
     * If none qualifies, falls back to the fairest available color.
     * Returns the chosen color and its coverage radius.
     */
    fun pickSafeColor(reachBlocks: Int): Pair<DyeColor, Int> {
        val layout = currentLayout!!
        val coverageOf = { color: DyeColor -> layout.coverage[color] ?: LARGE_COVERAGE }

        val fair = existingColors.filter { coverageOf(it) <= reachBlocks }
        val chosen = if (fair.isNotEmpty()) fair.random() else existingColors.minBy(coverageOf)

        // an unreachable (disconnected) coverage can't be fixed with more time, so report 0
        val coverage = coverageOf(chosen).let { if (it >= LARGE_COVERAGE) 0 else it }

        return chosen to coverage
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
        override fun group(pos: BlockPos): Int = sqrt(pos.distSqr(floorShape.center())).roundToInt()
    }

    inner class Taxicab(override val minColors: Int = 7, override val maxColors: Int = 9) : Pattern {
        override fun group(pos: BlockPos): Int = pos.distManhattan(floorShape.center())
    }

    inner class Chebyshev(override val minColors: Int = 6, override val maxColors: Int = 9) : Pattern {
        override fun group(pos: BlockPos): Int = pos.distChessboard(floorShape.center())
    }

    inner class Parabola(override val minColors: Int = 10, override val maxColors: Int = 12) : Pattern {
        override fun group(pos: BlockPos): Int {
            val c = Vec3.atCenterOf(floorShape.center())
            return pos.distToCenterSqr(c.x, c.y, c.z).roundToInt()
        }
    }

    inner class Diagonal(override val minColors: Int = 6, override val maxColors: Int = 8) : Pattern {
        override fun group(pos: BlockPos): Int = pos.distManhattan(floorShape.min())
    }

    inner class Angled : Pattern {
        var subdivisions = 5
        var refAngle = 0.0

        override fun init() {
            subdivisions = Random.nextInt(8, 14)
            refAngle = Random.nextDouble() * PI * 2
        }

        override fun group(pos: BlockPos): Int {
            val dir = Vec3.atCenterOf(pos).subtract(Vec3.atCenterOf(floorShape.center())).normalize()
            val angleY = MathUtil.angleY(dir.x, dir.z)
            val angle = (angleY + refAngle + 2 * PI) % (2 * PI)

            return (angle / (2 * PI) * subdivisions).toInt()
        }
    }

    inner class Voronoi(override val minColors: Int = 6, override val maxColors: Int = 8) : Pattern {
        private var seeds: List<Vec3> = emptyList()

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
            arms = Random.nextInt(5, 9)
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
            scale = Random.nextDouble(0.1, 0.25)
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
            scale = Random.nextDouble(0.03, 0.08)
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

    class Hexagons(override val minColors: Int = 8, override val maxColors: Int = 11) : Pattern {
        private var size = 3

        override fun init() {
            size = Random.nextInt(2, 5)
        }

        override fun group(pos: BlockPos): Int {
            val hex = pointyHexRound(pos.x.toDouble(), pos.z.toDouble(), size.toDouble())
            return hash(hex[0], hex[1])
        }
    }

    class Triangles(override val minColors: Int = 8, override val maxColors: Int = 11) : Pattern {
        private var size = 3

        override fun init() {
            size = Random.nextInt(2, 5)
        }

        override fun group(pos: BlockPos): Int {
            val s = size.toDouble()
            val col = floor(pos.x / s).toInt()
            val row = floor(pos.z / s).toInt()
            val fx = pos.x / s - col
            val fz = pos.z / s - row

            val half = if ((col + row) and 1 == 0) {
                if (fx + fz < 1.0) 0 else 1
            } else {
                if (fx < fz) 0 else 1
            }

            return hash(col, row, half)
        }
    }

    class EinsteinTiles(override val minColors: Int = 8, override val maxColors: Int = 11) : Pattern {
        private var size = 4
        private var phase = 0.0

        override fun init() {
            size = Random.nextInt(3, 6)
            phase = Random.nextDouble() * PI * 2
        }

        override fun group(pos: BlockPos): Int {
            val s = size.toDouble()
            val hex = pointyHexRound(pos.x.toDouble(), pos.z.toDouble(), s)
            val q = hex[0]
            val r = hex[1]

            val cx = s * sqrt(3.0) * (q + r / 2.0)
            val cz = s * 1.5 * r

            var angle = atan2(pos.z - cz, pos.x - cx) + phase
            angle = (angle % (2 * PI) + 2 * PI) % (2 * PI)
            val sextant = (angle / (2 * PI) * 6).toInt()

            return hash(q, r, sextant)
        }
    }

    inner class Penrose(
        override val minColors: Int = 8,
        override val maxColors: Int = 11,
        val penroseDeflations: Int = 5,
    ) : Pattern {
        private var cells = HashMap<Long, Int>()

        override fun init() {
            val cells = HashMap<Long, Int>()

            val phi = (1.0 + sqrt(5.0)) / 2.0
            val center = floorShape.center()
            val bounds = floorShape.bounds()
            val cx = center.x + 0.5
            val cz = center.z + 0.5
            val radius = max(bounds.width(), bounds.length()).toDouble()

            // initial "sun" wheel of 10 Robinson triangles around the floor center
            var triangles = ArrayList<DoubleArray>()

            for (i in 0 until 10) {
                var b1x = cx + radius * cos((2 * i - 1) * PI / 10.0)
                var b1z = cz + radius * sin((2 * i - 1) * PI / 10.0)
                var b2x = cx + radius * cos((2 * i + 1) * PI / 10.0)
                var b2z = cz + radius * sin((2 * i + 1) * PI / 10.0)

                if (i % 2 == 0) {
                    val tx = b1x; val tz = b1z
                    b1x = b2x; b1z = b2z
                    b2x = tx; b2z = tz
                }

                triangles.add(doubleArrayOf(0.0, cx, cz, b1x, b1z, b2x, b2z))
            }

            repeat(penroseDeflations) {
                val next = ArrayList<DoubleArray>(triangles.size * 3)

                for (t in triangles) {
                    val type = t[0].toInt()
                    val ax = t[1]; val az = t[2]
                    val bx = t[3]; val bz = t[4]
                    val ccx = t[5]; val ccz = t[6]

                    if (type == 0) {
                        val px = ax + (bx - ax) / phi
                        val pz = az + (bz - az) / phi
                        next.add(doubleArrayOf(0.0, ccx, ccz, px, pz, bx, bz))
                        next.add(doubleArrayOf(1.0, px, pz, ccx, ccz, ax, az))
                    } else {
                        val qx = bx + (ax - bx) / phi
                        val qz = bz + (az - bz) / phi
                        val rx = bx + (ccx - bx) / phi
                        val rz = bz + (ccz - bz) / phi
                        next.add(doubleArrayOf(1.0, rx, rz, ccx, ccz, ax, az))
                        next.add(doubleArrayOf(1.0, qx, qz, rx, rz, bx, bz))
                        next.add(doubleArrayOf(0.0, rx, rz, qx, qz, ax, az))
                    }
                }

                triangles = next
            }

            for (x in bounds.min().x..bounds.max().x) {
                for (z in bounds.min().z..bounds.max().z) {
                    val px = x + 0.5
                    val pz = z + 0.5

                    for ((idx, t) in triangles.withIndex()) {
                        if (pointInTriangle(px, pz, t[1], t[2], t[3], t[4], t[5], t[6])) {
                            cells[packKey(x, z)] = idx
                            break
                        }
                    }
                }
            }

            this.cells = cells
        }

        override fun group(pos: BlockPos): Int = cells[packKey(pos.x, pos.z)] ?: 0
    }

    inner class HilbertCurve(override val minColors: Int = 8, override val maxColors: Int = 12) : Pattern {
        private var order = 16
        private var segLen = 3
        private var minX = 0
        private var minZ = 0

        override fun init() {
            val bounds = floorShape.bounds()
            minX = bounds.min().x
            minZ = bounds.min().z

            val maxDim = max(bounds.width(), bounds.length())
            var n = 1
            while (n < maxDim) n = n shl 1
            order = n

            segLen = Random.nextInt(2, 6)
        }

        override fun group(pos: BlockPos): Int = xy2d(order, pos.x - minX, pos.z - minZ) / segLen

        private fun xy2d(n: Int, x0: Int, z0: Int): Int {
            var d = 0
            var x = x0
            var y = z0
            var s = n / 2

            while (s > 0) {
                val rx = if (x and s > 0) 1 else 0
                val ry = if (y and s > 0) 1 else 0
                d += s * s * ((3 * rx) xor ry)

                if (ry == 0) {
                    if (rx == 1) {
                        x = n - 1 - x
                        y = n - 1 - y
                    }
                    val t = x
                    x = y
                    y = t
                }

                s = s shr 1
            }

            return d
        }
    }
}

private fun pointyHexRound(x: Double, z: Double, size: Double): IntArray {
    val q = (sqrt(3.0) / 3.0 * x - 1.0 / 3.0 * z) / size
    val r = (2.0 / 3.0 * z) / size
    val s = -q - r

    var rq = round(q)
    var rr = round(r)
    val rs = round(s)

    val dq = abs(rq - q)
    val dr = abs(rr - r)
    val ds = abs(rs - s)

    if (dq > dr && dq > ds) rq = -rr - rs
    else if (dr > ds) rr = -rq - rs

    return intArrayOf(rq.toInt(), rr.toInt())
}

private fun pointInTriangle(
    px: Double, pz: Double,
    ax: Double, az: Double,
    bx: Double, bz: Double,
    cx: Double, cz: Double
): Boolean {
    val d1 = edgeSign(px, pz, ax, az, bx, bz)
    val d2 = edgeSign(px, pz, bx, bz, cx, cz)
    val d3 = edgeSign(px, pz, cx, cz, ax, az)

    val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
    val hasPos = d1 > 0 || d2 > 0 || d3 > 0

    return !(hasNeg && hasPos)
}

private fun edgeSign(px: Double, pz: Double, ax: Double, az: Double, bx: Double, bz: Double): Double =
    (px - bx) * (az - bz) - (ax - bx) * (pz - bz)
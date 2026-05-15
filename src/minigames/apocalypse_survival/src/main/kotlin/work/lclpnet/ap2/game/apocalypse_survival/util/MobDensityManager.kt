package work.lclpnet.ap2.game.apocalypse_survival.util

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.phys.Vec3
import work.lclpnet.gaco.ds.IndexedSet
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapUtils
import java.util.Random
import kotlin.math.ceil
import kotlin.math.floor

private const val CELL_SIZE = 25.0

class MobDensityManager(map: GameMap, private val random: Random) {

    private val centerX: Double
    private val centerZ: Double
    private val cellsX: Int
    private val cellsZ: Int
    private val cells: IntArray
    private val cellsWithLeast: IndexedSet<Int>
    private val mobs = HashMap<Mob, MobPos>()
    private var minMobCount = 0

    init {
        val spawn = MapUtils.getSpawnPosition(map)
        val radiusNum = map.requireProperty<Number>("bounding-box-radius")

        centerX = spawn.x()
        centerZ = spawn.z()

        val radius = radiusNum.toDouble()
        val count = 2 * maxOf(1, ceil(radius / CELL_SIZE).toInt())
        cellsX = count
        cellsZ = count

        cells = IntArray(cellsX * cellsZ)

        cellsWithLeast = IndexedSet(cellsX * cellsZ)
        for (i in 0 until cellsX * cellsZ) {
            cellsWithLeast.add(i)
        }
    }

    fun cellWithLeastMobs(): Int {
        check(!cellsWithLeast.isEmpty()) { "Least mob cells list is empty, this shouldn't happen" }

        return cellsWithLeast.get(random.nextInt(cellsWithLeast.size))
    }

    fun cellAt(x: Double, z: Double): Int? {
        val cellX = floor(x / CELL_SIZE).toInt() + cellsX / 2
        val cellZ = floor(z / CELL_SIZE).toInt() + cellsZ / 2

        if (cellX !in 0..<cellsX || cellZ !in 0..<cellsZ) return null

        return cellX + cellZ * cellsX
    }

    fun getX(cell: Int) = centerX + CELL_SIZE * (cell % cellsX - cellsX * 0.5)

    fun getZ(cell: Int) = centerZ + CELL_SIZE * (Math.floorDiv(cell, cellsX) - cellsZ * 0.5)

    fun startTracking(mob: Mob) {
        val mobPos = MobPos(mob)
        mobs[mob] = mobPos
        updateMobPos(mobPos)
    }

    fun stopTracking(mob: Mob) {
        val mobPos = mobs.remove(mob) ?: return
        removeFromCell(mobPos)
    }

    fun startGuarding(mob: PathfinderMob): Vec3? {
        val mobPos = mobs[mob] ?: return null

        val guardPos = findGuardPos(mob) ?: return null

        mobPos.guardPos = guardPos
        updateMobPos(mobPos)

        return guardPos
    }

    fun stopGuarding(mob: PathfinderMob) {
        val mobPos = mobs[mob] ?: return

        mobPos.guardPos = null
        updateMobPos(mobPos)
    }

    private fun updateMobPos(mobPos: MobPos) {
        val newCell = cellAt(mobPos.getX(), mobPos.getZ())

        if ((newCell != null && mobPos.hasCell && mobPos.cell == newCell)
            || (newCell == null && !mobPos.hasCell)) return

        removeFromCell(mobPos)

        if (newCell == null) {
            mobPos.hasCell = false
            return
        }

        mobPos.cell = newCell
        addToCell(mobPos)
    }

    private fun addToCell(mobPos: MobPos) {
        val newMobCount = ++cells[mobPos.cell]

        if (newMobCount != minMobCount + 1) return

        cellsWithLeast.remove(mobPos.cell)

        if (!cellsWithLeast.isEmpty()) return

        minMobCount = newMobCount

        for (i in 0 until cellsX * cellsZ) {
            if (cells[i] == newMobCount) {
                cellsWithLeast.add(i)
            }
        }
    }

    private fun removeFromCell(mobPos: MobPos) {
        if (!mobPos.hasCell) return

        val newMobCount = --cells[mobPos.cell]

        when {
            newMobCount < minMobCount -> {
                minMobCount = newMobCount
                cellsWithLeast.clear()
                cellsWithLeast.add(mobPos.cell)
            }
            newMobCount == minMobCount -> cellsWithLeast.add(mobPos.cell)
        }
    }

    private fun findGuardPos(mob: PathfinderMob): Vec3? {
        val cell = cellWithLeastMobs()

        val x = getX(cell) + 0.5 * CELL_SIZE
        val z = getZ(cell) + 0.5 * CELL_SIZE

        return LandRandomPos.getPosTowards(mob, 20, 10, Vec3(x, mob.y, z))
    }

    fun update() {
        for (mobPos in mobs.values) {
            updateMobPos(mobPos)
        }
    }

    private class MobPos(val mob: Mob) {
        var guardPos: Vec3? = null
        var cell: Int = 0
        var hasCell: Boolean = false

        fun getX() = guardPos?.x() ?: mob.x
        fun getZ() = guardPos?.z() ?: mob.z
    }
}

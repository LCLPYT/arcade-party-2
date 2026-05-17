package work.lclpnet.ap2.game.maniac_digger.data

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.mc.KibuBlockPos
import work.lclpnet.kibu.schematic.FabricStructureWrapper
import work.lclpnet.kibu.structure.BlockStructure
import kotlin.math.abs
import kotlin.math.sign

class MdPipePlan(
    dimensions: Vec3i,
    private val diameter: Int,
    val spawn: Vec3,
    val bounds: BlockBox,
    private val fillMaterial: (BlockPos) -> BlockState
) {
    companion object {
        val WALL_MATERIAL: BlockState = Blocks.BEDROCK.defaultBlockState()
    }

    private val struct: FabricStructureWrapper
    private val tmpPos = BlockPos.MutableBlockPos()

    init {
        val structure = FabricStructureWrapper.createArrayStructure(dimensions.x, dimensions.y, dimensions.z, KibuBlockPos())
        struct = FabricStructureWrapper(structure)
    }

    fun structure(): BlockStructure = struct.structure

    fun placeVertical(pos: BlockPos, depth: Int) {
        val d = diameter - 1

        for (x in 0 until diameter) {
            for (z in 0 until diameter) {
                tmpPos.setWithOffset(pos, x, 0, z)

                if (!struct.getBlockState(tmpPos).isAir) continue

                if (depth == 0 || x == 0 || x == d || z == 0 || z == d) {
                    struct.setBlockState(tmpPos, WALL_MATERIAL)
                } else {
                    struct.setBlockState(tmpPos, fillMaterial(tmpPos))
                }
            }
        }
    }

    fun placeHorizontal(pos: BlockPos.MutableBlockPos, pos2: BlockPos.MutableBlockPos) {
        if (pos.y != pos2.y) {
            throw IllegalStateException("Positions are on different heights")
        }

        clearWall(pos, pos2)

        val dx = abs(pos.x - pos2.x) + diameter
        val dz = abs(pos.z - pos2.z) + diameter

        val minX = minOf(pos.x, pos2.x)
        val minZ = minOf(pos.z, pos2.z)
        val minY = pos.y

        for (x in 0 until dx) {
            for (z in 0 until dz) {
                for (y in 0 until diameter) {
                    tmpPos.set(minX + x, minY + y, minZ + z)

                    if (!struct.getBlockState(tmpPos).isAir) continue

                    if (x == 0 || x == dx - 1 || z == 0 || z == dz - 1 || y == 0 || y == diameter - 1) {
                        struct.setBlockState(tmpPos, WALL_MATERIAL)
                    } else {
                        struct.setBlockState(tmpPos, fillMaterial(tmpPos))
                    }
                }
            }
        }

        fixFloor(pos2)
    }

    private fun clearWall(pos: BlockPos.MutableBlockPos, pos2: BlockPos.MutableBlockPos) {
        val dx = sign((pos2.x - pos.x).toDouble()).toInt()
        val dz = sign((pos2.z - pos.z).toDouble()).toInt()

        if (dx != 0 && dz != 0 || dx == dz) {
            throw IllegalStateException("Invalid direction")
        }

        val ox = if (dx > 0) dx * diameter - 1 else 0
        val oz = if (dz > 0) dz * diameter - 1 else 0
        val ax = if (dx == 0) 1 else 0
        val az = if (dz == 0) 1 else 0

        val air = Blocks.AIR.defaultBlockState()

        tmpPos.setWithOffset(pos, ox + ax, 1, oz + az)
        struct.setBlockState(tmpPos, air)
        tmpPos.setWithOffset(pos, ox + 2 * ax, 1, oz + 2 * az)
        struct.setBlockState(tmpPos, air)
        tmpPos.setWithOffset(pos, ox + ax, 2, oz + az)
        struct.setBlockState(tmpPos, air)
        tmpPos.setWithOffset(pos, ox + 2 * ax, 2, oz + 2 * az)
        struct.setBlockState(tmpPos, air)
    }

    private fun fixFloor(pos: BlockPos.MutableBlockPos) {
        tmpPos.setWithOffset(pos, 1, 0, 1)
        struct.setBlockState(tmpPos, fillMaterial(tmpPos))

        tmpPos.setWithOffset(pos, 2, 0, 1)
        struct.setBlockState(tmpPos, fillMaterial(tmpPos))

        tmpPos.setWithOffset(pos, 1, 0, 2)
        struct.setBlockState(tmpPos, fillMaterial(tmpPos))

        tmpPos.setWithOffset(pos, 2, 0, 2)
        struct.setBlockState(tmpPos, fillMaterial(tmpPos))
    }

    fun setBlockState(pos: BlockPos, state: BlockState) {
        struct.setBlockState(pos, state)
    }
}

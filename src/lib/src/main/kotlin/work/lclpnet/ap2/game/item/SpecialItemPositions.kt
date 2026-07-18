package work.lclpnet.ap2.game.item

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.gaco.ds.StructureMask
import work.lclpnet.gaco.ds.WeightedList
import work.lclpnet.kibu.util.math.Matrix3i
import java.util.*

class SpecialItemPositions(
    private val validPos: BlockPredicate,
    private val debugController: DebugController,
) {
    private var spawnBoxes: WeightedList<BlockBox>? = null
    private var shape: BlockShape? = null
    private var mask: StructureMask? = null

    fun setShape(shape: BlockShape) {
        this.shape = shape
        mask = StructureMask.createEmpty(shape.bounds())
    }

    @Synchronized
    fun update() {
        val mask = mask

        if (shape == null || mask == null) return

        debugController.stopWatch().start("scan")

        val minPos = shape!!.bounds().min()

        for (pos in shape) {
            val valid = validPos.test(pos)

            mask.setVoxelAt(
                pos.x - minPos.x,
                pos.y - minPos.y,
                pos.z - minPos.z,
                valid
            )
        }

        debugController.stopWatch().start("meshing")

        val boxes = mask.greedyMeshing().generateBoxes()
        spawnBoxes = WeightedList.of(boxes) { it.volume() }

        if (DEBUG_TIMINGS) {
            debugController.stopWatch().printResults(System.out)
        }

        if (DEBUG_SPAWNS) {
            debugController.exclusive("spawn_boxes") { controller ->
                controller.visualizeBoxes(
                    boxes,
                    minPos,
                    Matrix3i.IDENTITY,
                    Blocks.STAINED_GLASS.lime().defaultBlockState()
                )
            }
        }
    }

    fun randomPos(random: Random): BlockPos? {
        val spawnBoxes = spawnBoxes
        val shape = shape

        if (spawnBoxes == null || shape == null) {
            return null
        }

        val box = spawnBoxes.getRandomElement(random) ?: return null

        val pos = BlockPos.MutableBlockPos()
        box.randomBlockPos(pos, random)
        pos.move(shape.bounds().min())

        return pos
    }

    companion object {
        private const val DEBUG_SPAWNS = false
        private const val DEBUG_TIMINGS = false
    }
}

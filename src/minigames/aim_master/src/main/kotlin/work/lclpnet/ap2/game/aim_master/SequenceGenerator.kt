package work.lclpnet.ap2.game.aim_master

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import java.util.Random

class SequenceGenerator(
    private val posGen: PositionGenerator,
    private val blockOps: BlockOptions,
    private val scoreGoal: Int
) {
    val sequence: AimMasterSequence = generateSequence()

    private fun generateSequence(): AimMasterSequence {
        val sequence = AimMasterSequence()
        val random = Random()

        repeat(scoreGoal) {
            val posBlockMap = HashMap<BlockPos, Block>()
            val positions = posGen.pickPositions()
            val options = blockOps.getBlockOptions()

            for (pos in positions) {
                val r = random.nextInt(options.size)
                val block = options.remove(r)
                posBlockMap[pos] = block
            }

            val targetIndex = random.nextInt(positions.size)
            sequence.items.add(AimMasterSequence.Item(posBlockMap, positions[targetIndex]))
        }

        return sequence
    }
}

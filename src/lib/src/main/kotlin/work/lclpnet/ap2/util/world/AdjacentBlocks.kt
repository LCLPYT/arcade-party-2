package work.lclpnet.ap2.util.world

import net.minecraft.core.BlockPos

/**
 * Provides adjacent blocks of a block.
 */
interface AdjacentBlocks {
    fun getAdjacent(pos: BlockPos): Iterator<BlockPos>

    fun iterate(pos: BlockPos): Iterable<BlockPos> {
        return Iterable { getAdjacent(pos) }
    }
}

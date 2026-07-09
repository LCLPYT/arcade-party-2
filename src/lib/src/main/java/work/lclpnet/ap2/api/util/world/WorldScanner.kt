package work.lclpnet.ap2.api.util.world

import net.minecraft.core.BlockPos

interface WorldScanner {

    fun scan(starts: Set<BlockPos>): Iterator<BlockPos>

    fun scan(start: BlockPos): Iterator<BlockPos> =
        scan(setOf(start))
}

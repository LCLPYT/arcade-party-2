package work.lclpnet.ap2.impl.util.world

import net.minecraft.core.BlockPos
import work.lclpnet.ap2.util.world.AdjacentBlocks
import work.lclpnet.ap2.util.world.WorldScanner

class BfsWorldScanner(private val adjacentBlocks: AdjacentBlocks) : WorldScanner {

    override fun scan(starts: Set<BlockPos>): Iterator<BlockPos> {
        val queue = ArrayList(starts)
        val known = HashSet(starts)

        return object : Iterator<BlockPos> {

            override fun hasNext(): Boolean = !queue.isEmpty()

            override fun next(): BlockPos {
                val current = queue.removeFirst()

                advance(current)

                return current
            }

            fun advance(pos: BlockPos) {
                for (adj in adjacentBlocks.iterate(pos)) {
                    if (known.contains(adj)) continue

                    val immutable = adj.immutable()
                    queue.add(immutable)
                    known.add(immutable)
                }
            }
        }
    }
}

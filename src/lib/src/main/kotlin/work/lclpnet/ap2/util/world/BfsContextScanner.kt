package work.lclpnet.ap2.util.world

import net.minecraft.core.BlockPos

data class ScannerNode(
    val pos: BlockPos,
    val distance: Int,
)

class BfsContextScanner(val adjacentBlocks: AdjacentBlocks) {

    fun scan(start: BlockPos): Iterator<ScannerNode> {
        val queue = mutableListOf<ScannerNode>()
        val known = mutableSetOf<BlockPos>()

        queue.add(ScannerNode(start, 0))
        known.add(start)

        return object : AbstractIterator<ScannerNode>() {
            override fun computeNext() {
                if (queue.isEmpty()) {
                    done()
                    return
                }

                val node = queue.removeFirst()

                advance(node)

                setNext(node)
            }

            fun advance(node: ScannerNode) {
                for (pos in adjacentBlocks.getAdjacent(node.pos)) {
                    if (known.contains(pos)) continue

                    val copy = pos.immutable()

                    queue.add(ScannerNode(copy, node.distance + 1))
                    known.add(copy)
                }
            }
        }
    }
}
package work.lclpnet.ap2.game.splashy_dropper.data

import net.minecraft.core.BlockPos

val ADJ_OFFSETS = intArrayOf(1, 0, 1, 1, 0, 1, -1, 0, -1, -1, 0, -1, 1, -1, -1, 1)

fun sdShapeSquare(width: Int, length: Int): SdShape {
    require(width >= 1 && length >= 1) { "Square dimensions must be positive" }

    val positions = BlockPos.betweenClosedStream(0, 0, 0, width - 1, 0, length - 1)
        .map { it.immutable() }
        .toList()
        .toTypedArray()

    return SdShape(positions)
}

private fun computeAdjacent(positions: Array<BlockPos>): Array<BlockPos> {
    val set = HashSet<BlockPos>()

    for (pos in positions) {
        for (i in ADJ_OFFSETS.indices step 2) {
            set.add(pos.offset(ADJ_OFFSETS[i], 0, ADJ_OFFSETS[i + 1]))
        }
    }

    for (pos in positions) {
        set.remove(pos)
    }

    return set.toTypedArray()
}

class SdShape(val positions: Array<BlockPos>, val adjacent: Array<BlockPos>) {

    constructor(positions: Array<BlockPos>) : this(positions, computeAdjacent(positions))

    fun hasSpace(space: Set<BlockPos>, pos: BlockPos): Boolean =
        positions.all { shapePos -> space.contains(pos.offset(shapePos)) }

    fun combinedPositions(): Iterator<BlockPos> =
        (positions.asSequence() + adjacent.asSequence()).iterator()
}

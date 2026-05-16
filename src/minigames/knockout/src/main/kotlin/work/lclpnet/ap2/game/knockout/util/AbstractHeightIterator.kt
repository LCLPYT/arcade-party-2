package work.lclpnet.ap2.game.knockout.util

import net.minecraft.core.BlockPos

abstract class AbstractHeightIterator(
    protected val radius: Int,
    protected val centerX: Int,
    protected val centerZ: Int,
    protected val minY: Int,
    protected val maxY: Int
) : Iterator<BlockPos> {

    private var hasNext = false
    private var done = false
    private var has2d = false
    private val current = BlockPos.MutableBlockPos()
    protected var rx = 0
    protected var rz = 0
    private var y = minY

    override fun hasNext(): Boolean {
        if (!hasNext) advance()
        return !done
    }

    override fun next(): BlockPos {
        hasNext = false
        return current
    }

    private fun advance() {
        if (!has2d) {
            if (!advance2d()) {
                done = true
                hasNext = true
                return
            }
            has2d = true
        }

        if (y > maxY) {
            has2d = false
            y = minY
            advance()
            return
        }

        current.set(centerX + rx - radius, y++, centerZ + rz - radius)
        hasNext = true
    }

    protected abstract fun advance2d(): Boolean
}

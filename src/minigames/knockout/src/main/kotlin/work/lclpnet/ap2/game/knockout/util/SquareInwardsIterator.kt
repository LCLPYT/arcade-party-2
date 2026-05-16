package work.lclpnet.ap2.game.knockout.util

val DX = intArrayOf(1, 0, -1, 0)
val DZ = intArrayOf(0, 1, 0, -1)

class SquareInwardsIterator(
    radius: Int,
    centerX: Int,
    centerZ: Int,
    minY: Int,
    maxY: Int
) : AbstractHeightIterator(radius, centerX, centerZ, minY, maxY) {

    private var first = true
    private var r = 2 * radius
    private var steps = r
    private var repeat = 2
    private var mode = 0

    override fun advance2d(): Boolean {
        if (first) {
            first = false
            return true
        }

        if (r <= 0) return false

        if (steps > 0) {
            rx += DX[mode]
            rz += DZ[mode]
            steps--
            return true
        }

        mode = (mode + 1) % 4

        if (repeat > 0) {
            repeat--
        } else {
            r--
            repeat = 1
        }

        steps = r

        return advance2d()
    }
}

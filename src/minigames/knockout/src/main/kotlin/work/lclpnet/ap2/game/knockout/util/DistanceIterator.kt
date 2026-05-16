package work.lclpnet.ap2.game.knockout.util

class DistanceIterator(
    radius: Int,
    centerX: Int,
    centerZ: Int,
    minY: Int,
    maxYInclusive: Int,
    private val distances: Array<ShortArray>,
    private val targetDistance: Int
) : AbstractHeightIterator(radius, centerX, centerZ, minY, maxYInclusive) {

    private val len = distances.size
    private var first = true

    override fun advance2d(): Boolean {
        if (first) {
            rx--
            first = false
        }

        var distance: Short

        do {
            rx++
            if (rx >= len) {
                rx = 0
                rz++
            }
            if (rz >= len) return false
            distance = distances[rx][rz]
        } while (distance.toInt() != targetDistance)

        return true
    }
}

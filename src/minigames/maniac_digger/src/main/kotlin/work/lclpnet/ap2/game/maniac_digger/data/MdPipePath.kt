package work.lclpnet.ap2.game.maniac_digger.data

import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

class MdPipePath(val waypoints: List<Vec3>) {

    fun progressToGoal(pos: Vec3): Double {
        if (waypoints.size < 2) return 0.0

        var cumulative = 0.0
        var bestDistanceSq = Double.MAX_VALUE
        var bestProgress = 0.0

        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]

            val abx = b.x - a.x
            val aby = b.y - a.y
            val abz = b.z - a.z
            val legLengthSq = abx * abx + aby * aby + abz * abz
            val legLength = sqrt(legLengthSq)

            val t = if (legLengthSq == 0.0) 0.0 else {
                val dot = (pos.x - a.x) * abx + (pos.y - a.y) * aby + (pos.z - a.z) * abz
                (dot / legLengthSq).coerceIn(0.0, 1.0)
            }

            val dx = pos.x - (a.x + t * abx)
            val dy = pos.y - (a.y + t * aby)
            val dz = pos.z - (a.z + t * abz)
            val distanceSq = dx * dx + dy * dy + dz * dz

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestProgress = cumulative + t * legLength
            }

            cumulative += legLength
        }

        return bestProgress
    }

    fun translate(dx: Double, dy: Double, dz: Double) = MdPipePath(waypoints.map { it.add(dx, dy, dz) })
}

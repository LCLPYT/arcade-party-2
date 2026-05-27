package work.lclpnet.ap2.game.red_light_green_light

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import work.lclpnet.gaco.ds.BlockBox
import java.util.*

private const val MIN_TRACK_DISTANCE = 3.0
private const val MAX_POSITIONS = 5

class MovementTracker(private val goal: BlockBox) {

    private val entries = HashMap<UUID, Entry>()

    fun track(player: ServerPlayer) {
        getEntry(player).update(player.position())
    }

    fun getMostDistantPos(player: ServerPlayer): Vec3? {
        return getEntry(player).getMostDistantPos()
    }

    private fun getEntry(player: ServerPlayer): Entry {
        return entries.computeIfAbsent(player.uuid) { Entry() }
    }

    private inner class Entry {
        private val positions = arrayOfNulls<Vec3>(MAX_POSITIONS)
        private var pointer = -1
        private var tracked = 0

        fun update(pos: Vec3) {
            val lastPos = getLastPos()

            if (lastPos == null) {
                track(pos)
                return
            }

            if (lastPos.distanceToSqr(pos) < MIN_TRACK_DISTANCE * MIN_TRACK_DISTANCE) return
            if (goal.squaredDistanceTo(pos) >= goal.squaredDistanceTo(lastPos)) return

            track(pos)
        }

        private fun track(pos: Vec3) {
            pointer = (pointer + 1) % MAX_POSITIONS
            positions[pointer] = pos
            tracked = minOf(tracked + 1, MAX_POSITIONS)
        }

        private fun getLastPos(): Vec3? {
            if (pointer == -1) return null
            return positions[pointer]
        }

        fun getMostDistantPos(): Vec3? {
            if (tracked == 0) return null
            if (tracked == MAX_POSITIONS) return positions[(pointer + 1) % MAX_POSITIONS]
            return positions[0]
        }
    }
}

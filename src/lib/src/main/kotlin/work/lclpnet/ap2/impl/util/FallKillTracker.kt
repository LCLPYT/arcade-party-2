package work.lclpnet.ap2.impl.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.kibu.hook.util.OnGroundDetector
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import java.util.*

class FallKillTracker(
    private val participants: Participants,
    private val memoryTicks: Int = 20,
) {

    private val brokenAreas = ArrayList<BrokenArea>()
    private val killers = HashMap<UUID, UUID>()

    fun init(scheduler: TaskScheduler) {
        scheduler.interval(1) { -> tick() }
    }

    fun onBlockBroken(pos: BlockPos, breaker: ServerPlayer) {
        onAreaBroken(BlockBox.of(pos), breaker)
    }

    fun onAreaBroken(box: BlockBox, breaker: ServerPlayer) {
        brokenAreas.add(BrokenArea(box, breaker.uuid, memoryTicks))
    }

    fun getKiller(victim: ServerPlayer): UUID? = killers[victim.uuid]

    fun forget(victim: ServerPlayer) {
        killers.remove(victim.uuid)
    }

    private fun tick() {
        val it = brokenAreas.iterator()

        while (it.hasNext()) {
            if (--it.next().ticksLeft <= 0) it.remove()
        }

        for (player in participants) {
            // safely on the ground means the fall is over -> drop any latched killer
            if (OnGroundDetector.isOnGroundServer(player) && !player.isInLava) {
                killers.remove(player.uuid)
                continue
            }

            val box = player.boundingBox

            // most recently broken intersecting area wins
            for (i in brokenAreas.indices.reversed()) {
                val area = brokenAreas[i]

                if (area.breaker == player.uuid) continue

                if (area.box.intersects(box)) {
                    killers[player.uuid] = area.breaker
                    break
                }
            }
        }
    }

    private class BrokenArea(
        val box: BlockBox,
        val breaker: UUID,
        var ticksLeft: Int,
    )
}

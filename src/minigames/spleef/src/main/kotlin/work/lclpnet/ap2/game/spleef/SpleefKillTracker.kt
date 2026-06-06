package work.lclpnet.ap2.game.spleef

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.kibu.hook.util.OnGroundDetector
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import java.util.*

class SpleefKillTracker(
    private val participants: Participants,
    private val memoryTicks: Int = 20,
) {

    private val brokenBlocks = ArrayList<BrokenBlock>()
    private val killers = HashMap<UUID, UUID>()

    fun init(scheduler: TaskScheduler) {
        scheduler.interval(1) { -> tick() }
    }

    fun onBlockBroken(pos: BlockPos, breaker: ServerPlayer) {
        brokenBlocks.add(BrokenBlock(pos.x, pos.y, pos.z, breaker.uuid, memoryTicks))
    }

    fun getKiller(victim: ServerPlayer): UUID? = killers[victim.uuid]

    fun forget(victim: ServerPlayer) {
        killers.remove(victim.uuid)
    }

    private fun tick() {
        val it = brokenBlocks.iterator()

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

            // most recently broken intersecting block wins
            for (i in brokenBlocks.indices.reversed()) {
                val block = brokenBlocks[i]

                if (block.breaker == player.uuid) continue

                if (box.intersects(
                        block.x.toDouble(), block.y.toDouble(), block.z.toDouble(),
                        (block.x + 1).toDouble(), (block.y + 1).toDouble(), (block.z + 1).toDouble()
                )) {
                    killers[player.uuid] = block.breaker
                    break
                }
            }
        }
    }

    private class BrokenBlock(
        val x: Int, val y: Int, val z: Int,
        val breaker: UUID,
        var ticksLeft: Int,
    )
}

package work.lclpnet.ap2.game.knockout.util

import com.google.common.collect.Iterables
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.BlockCollisions
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.base.Participants
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.gaco.collisions.util.PlayerAction
import work.lclpnet.kibu.hook.Hook
import work.lclpnet.kibu.hook.HookFactory
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import java.util.*

private const val DEBUG_IMPACT = false

fun interface OnImpact {
    fun onImpact(player: ServerPlayer, collisions: Iterable<BlockPos>)
}

class ImpactDetector(
    private val participants: Participants,
    private val debugController: DebugController,
    private val thresholdSpeed: Double
) {
    private val onImpact: Hook<OnImpact> = HookFactory.createArrayBacked(OnImpact::class.java) { hooks ->
        OnImpact { player, collisions ->
            for (hook in hooks) {
                hook.onImpact(player, collisions)
            }
        }
    }
    private val onMiss: Hook<PlayerAction> = HookFactory.createArrayBacked(PlayerAction::class.java) { hooks ->
        PlayerAction { player ->
            for (hook in hooks) {
                hook.act(player)
            }
        }
    }
    private val entries = HashMap<UUID, Entry>()

    fun enable(scheduler: TaskScheduler) {
        scheduler.interval(1) { -> tick() }
    }

    fun onImpact(): Hook<OnImpact> = onImpact

    fun onMiss(): Hook<PlayerAction> = onMiss

    private fun tick() {
        entries.entries.removeIf { !participants.isParticipating(it.key) }

        for (player in participants) {
            checkImpact(player)
        }
    }

    fun checkImpact(player: ServerPlayer) {
        val entry = entry(player)
        val prevPos = entry.pos
        val currentPos = player.position()

        entry.pos = currentPos

        if (prevPos == null) return

        val velocity = currentPos.subtract(prevPos)

        if (velocity.lengthSqr() > 1e-5) {
            checkImpact(player, velocity)
        }
    }

    fun checkImpact(player: ServerPlayer, velocity: Vec3) {
        val entry = entry(player)
        val prevSpeed = entry.speed
        val speed = velocity.length()

        entry.speed = speed
        entry.velocity = velocity

        if (speed < thresholdSpeed) {
            if (prevSpeed >= thresholdSpeed) {
                onMiss.invoker().act(player)
            }
            return
        }

        val dir = velocity.multiply(1.0, 0.0, 1.0).scale(1.0 / speed)
        val pos = player.position()
        val futurePos1 = pos.add(dir.scale(0.2)).add(0.0, 0.01, 0.0)
        val futurePos2 = pos.add(dir.scale(0.4)).add(0.0, 0.01, 0.0)

        val pose = player.getDimensions(player.pose)
        val futureBox1 = pose.makeBoundingBox(futurePos1)
        val futureBox2 = pose.makeBoundingBox(futurePos2)

        if (DEBUG_IMPACT) {
            debugController.exclusive("box_" + player.scoreboardName) { controller ->
                controller.renderer().ifPresent { r ->
                    r.marker(pos, Blocks.LIME_TERRACOTTA.defaultBlockState(), 0x06cc34)
                    r.box(futureBox1, Blocks.LIME_STAINED_GLASS.defaultBlockState())
                    r.box(futureBox2, Blocks.LIME_STAINED_GLASS.defaultBlockState())
                    r.text(pos.add(0.0, 0.25, 0.0), Component.literal(String.format("%.3f", speed)))
                    r.arrow(pos, dir, Blocks.BLUE_CONCRETE.defaultBlockState())
                }
            }
        }

        val collisions = Iterables.concat(collisions(player, futureBox1), collisions(player, futureBox2))
        val it = collisions.iterator()

        if (!it.hasNext()) return

        onImpact.invoker().onImpact(player, collisions)
    }

    private fun entry(player: ServerPlayer): Entry = entries.computeIfAbsent(player.uuid) { Entry() }

    private fun collisions(player: ServerPlayer, box: AABB): Iterable<BlockPos> {
        return Iterable { BlockCollisions(player.level(), player, box, false) { pos, _ -> pos } }
    }

    fun getVelocity(player: ServerPlayer): Vec3? = entries[player.uuid]?.velocity

    private class Entry {
        var pos: Vec3? = null
        var velocity: Vec3? = null
        var speed = 0.0
    }
}

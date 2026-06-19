package work.lclpnet.ap2.game.button_master

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import work.lclpnet.ap2.impl.util.RayCastUtil
import java.util.*

/** Interval (in ticks) of the periodic task that detects whether players could find or missed the button. */
const val BUTTON_SIGHT_CHECK_INTERVAL = 5
/** Maximum distance (in blocks) at which a player with line of sight is considered able to find the button. */
const val BUTTON_FIND_RANGE = 8.0
/** Distance (in blocks) a player has to move away from the button after being able to find it to miss it. */
const val BUTTON_MISS_RANGE = 12.0

/**
 * Detects when players miss the hidden button.
 *
 * A player that gets within [BUTTON_FIND_RANGE] blocks of the button with a clear line of sight is considered able
 * to find it. If such a player later moves further than [BUTTON_MISS_RANGE] blocks away from the button, it counts
 * as a miss.
 */
class ButtonMissDetector(
    private val world: ServerLevel,
    private val stats: ButtonMasterStats
) {
    private val canFindButton = mutableSetOf<UUID>()

    /** Forgets the per-player find state. To be called whenever a new button is placed. */
    fun reset() {
        canFindButton.clear()
    }

    /** Updates the find/miss state of every given player against the current button. */
    fun update(buttonPos: BlockPos, players: Iterable<ServerPlayer>) {
        val buttonCenter = Vec3.atCenterOf(buttonPos)

        for (player in players) {
            val distance = player.eyePosition.distanceTo(buttonCenter)

            if (player.uuid in canFindButton) {
                if (distance > BUTTON_MISS_RANGE) {
                    canFindButton.remove(player.uuid)
                    stats.buttonMissed(player)
                }
            } else if (distance <= BUTTON_FIND_RANGE && hasLineOfSight(player, buttonPos, buttonCenter, distance)) {
                canFindButton.add(player.uuid)
            }
        }
    }

    private fun hasLineOfSight(player: ServerPlayer, buttonPos: BlockPos, buttonCenter: Vec3, distance: Double): Boolean {
        if (distance < 1e-4) return true

        val eye = player.eyePosition
        val direction = buttonCenter.subtract(eye).normalize()

        val hit = RayCastUtil.raycastBlocks(
            world, eye, direction, distance,
            ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()
        )

        return hit.type == HitResult.Type.MISS || hit.blockPos == buttonPos
    }
}

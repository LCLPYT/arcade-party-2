package work.lclpnet.ap2.game.red_light_green_light

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.impl.util.movement.MovementListener
import work.lclpnet.gaco.collisions.util.PlayerAction
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.player.PlayerConnectionHooks
import work.lclpnet.kibu.hook.player.PlayerInputCallback
import work.lclpnet.kibu.hook.player.PlayerMoveCallback
import work.lclpnet.kibu.hook.util.PositionRotation
import java.util.*

private const val MIN_DISTANCE_SQ = 0.2 * 0.2

internal class RLGLMovementDetector {

    private val hook = PlayerAction.createHook()
    private val fixed = HashMap<UUID, Vec3>()

    fun init(hooks: HookRegistrar) {
        PlayerMoveCallback.HOOK.registerWith(hooks) { player, _, to ->
            onMove(player, to)
            false
        }

        PlayerConnectionHooks.QUIT.registerWith(hooks) { player -> fixed.remove(player.uuid) }
        PlayerInputCallback.HOOK.registerWith(hooks, ::onInput)
    }

    fun register(action: PlayerAction) {
        hook.register(action)
    }

    fun fixPosition(player: ServerPlayer) {
        fixed[player.uuid] = player.position()

        if (MovementListener.isMovementInput(player.lastClientInput)) {
            hook.invoker().act(player)
        }
    }

    fun unfixPosition(player: ServerPlayer) {
        fixed.remove(player.uuid)
    }

    fun unfixAll() {
        fixed.clear()
    }

    private fun onMove(player: ServerPlayer, to: PositionRotation) {
        val pos = fixed[player.uuid] ?: return

        val dx = pos.x - to.x()
        val dy = pos.y - to.y()
        val dz = pos.z - to.z()

        if (dx * dx + dy * dy + dz * dz >= MIN_DISTANCE_SQ) {
            hook.invoker().act(player)
        }
    }

    private fun onInput(player: ServerPlayer, input: Input) {
        if (fixed.containsKey(player.uuid) && MovementListener.isMovementInput(input)) {
            hook.invoker().act(player)
        }
    }
}

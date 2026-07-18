package work.lclpnet.ap2.game.item

import org.joml.Vector3d
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.animation.Animatable
import work.lclpnet.gaco.scene.animation.AnimationContext

internal class PickupAnimation(
    private val obj: Object3d,
    private val targetUpdater: (Vector3d) -> Unit,
    private val whenDone: () -> Unit,
) : Animatable {
    private val startPos = Vector3d()
    private val targetPos = Vector3d()
    private var time = 0.0
    private var done = false

    init {
        startPos.set(obj.position)
    }

    override fun updateAnimation(dt: Double, ctx: AnimationContext) {
        if (done) return

        targetUpdater(targetPos)

        time += dt

        var t = Math.clamp(time / DURATION_SECONDS, 0.0, 1.0)
        t *= t

        startPos.lerp(targetPos, t, obj.position)

        if (t >= 1.0) {
            done = true
            whenDone()
        }
    }

    companion object {
        private const val DURATION_SECONDS = 0.15
    }
}

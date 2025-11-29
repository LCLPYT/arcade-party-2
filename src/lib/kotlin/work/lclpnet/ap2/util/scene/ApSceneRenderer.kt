package work.lclpnet.ap2.util.scene

import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.Vec3i
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.`object`.BlockDisplayObject

class ApSceneRenderer(val scene: Scene) {

    fun display(obj: Object3d) {
        scene.add(obj)
    }

    fun markBlock(pos: Vec3i, state: BlockState, glowColor: Int): BlockDisplayObject {
        val marker = BlockDisplayObject(scene, getMarkerState(state))

        val margin = 0.015f

        marker.position.set(
            (pos.x + margin).toDouble(),
            (pos.y + margin).toDouble(),
            (pos.z + margin).toDouble()
        )

        marker.scale.set((1f - 2 * margin).toDouble())
        marker.isGlowing = true
        marker.glowColorOverride = glowColor

        display(marker)

        return marker
    }

    fun getMarkerState(state: BlockState): BlockState = when {
        state.isAir || state.isOf(Blocks.BARRIER) || state.isOf(Blocks.STRUCTURE_VOID) -> {
            Blocks.GLASS.defaultState
        }
        else -> state
    }
}
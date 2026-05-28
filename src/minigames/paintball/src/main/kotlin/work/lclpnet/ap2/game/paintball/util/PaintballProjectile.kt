package work.lclpnet.ap2.game.paintball.util

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.AnimationContext
import work.lclpnet.gaco.scene.physics.PhysicsBlockDisplayObject

const val TEAM_COLLISION_ENABLE_TICKS = 4

open class PaintballProjectile(scene: Scene, state: BlockState, world: ServerLevel) :
    PhysicsBlockDisplayObject(scene, state, world) {

    var ageTicks: Int = 0
        private set

    override fun updateAnimation(dt: Double, ctx: AnimationContext) {
        super.updateAnimation(dt, ctx)

        if (ageTicks++ == TEAM_COLLISION_ENABLE_TICKS) {
            enableTeamCollision()
        }
    }

    private fun enableTeamCollision() {
        val groups = rigidBody.collideWithGroups
        val group = rigidBody.collisionGroup
        rigidBody.setCollideWithGroups(groups or (group shr 1))
    }
}

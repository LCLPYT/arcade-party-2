package work.lclpnet.ap2.game.apocalypse_survival.goal

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.ext.mc.isIn
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.kibu.access.VelocityModifier
import java.util.*

private const val TOLERANCE = 1.5 * 1.5
private const val SCAN_TICKS = 50

class UnstuckGoal(private val mob: Mob, private val random: Random) : Goal() {

    private var lastPos: Vec3? = null
    private var notMovedTicks = 0
    private var flingTarget: Vec3? = null
    private var towardsTargetTicks = 0

    override fun canUse() = true

    override fun start() {
        lastPos = mob.position()
    }

    override fun tick() {
        val currentPos = mob.position()

        if (towardsTargetTicks > 0 && flingTarget != null) {
            towardsTargetTicks--
            val vel = flingTarget!!.subtract(currentPos).normalize().scale(0.6)
            VelocityModifier.setVelocity(mob, vel)
            return
        }

        if (lastPos!!.distanceToSqr(currentPos) > TOLERANCE) {
            lastPos = currentPos
            notMovedTicks = 0
            return
        }

        if (++notMovedTicks >= SCAN_TICKS) {
            notMovedTicks = 0
            unstuck()
        }
    }

    private fun unstuck() {
        destroyBlockage()
        destroyHideout()

        val nearbyPlayer = mob.level().getNearestPlayer(mob, 10.0)

        if (nearbyPlayer != null) {
            flingTarget = nearbyPlayer.eyePosition
            towardsTargetTicks = 4
            return
        }

        val pitch = -45 - random.nextFloat() * 25
        val yaw = random.nextFloat() * 360

        VelocityModifier.setVelocity(
            mob,
            Vec3.directionFromRotation(pitch, yaw).scale(0.6)
        )
    }

    private fun destroyHideout() {
        val target = mob.target ?: return

        if (mob.distanceToSqr(target) > TOLERANCE) return

        val aboveTarget = target.blockPosition().above()
        val world = mob.level()
        val state = world.getBlockState(aboveTarget)

        if (state.isIn(BlockTags.WOODEN_TRAPDOORS)) {
            world.destroyBlock(aboveTarget, false, mob)
            world.playSound(
                null,
                aboveTarget.x + 0.5, aboveTarget.y + 0.5, aboveTarget.z + 0.5,
                SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.75f, 1f
            )
        }
    }

    private fun destroyBlockage() {
        val world = mob.level()

        BlockPos.betweenClosedStream(mob.boundingBox)
            .filter { world.getBlockState(it).isOf(Blocks.COBWEB) }
            .forEach { world.destroyBlock(it, false, mob) }
    }
}

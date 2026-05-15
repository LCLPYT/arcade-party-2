package work.lclpnet.ap2.game.apocalypse_survival.goal

import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.game.apocalypse_survival.util.TargetManager
import java.util.EnumSet

class RoamGoal(
    private val mob: PathfinderMob,
    private val targetManager: TargetManager,
    private val speed: Double
) : Goal() {

    private var target: Vec3? = null

    init {
        flags = EnumSet.of(Flag.MOVE)
    }

    override fun canUse(): Boolean {
        if (mob.hasControllingPassenger() || mob.target != null || mob.navigation.isInProgress) {
            return false
        }

        target = targetManager.densityManager.startGuarding(mob)

        return target != null
    }

    override fun canContinueToUse() = !mob.navigation.isDone && !mob.hasControllingPassenger()

    override fun start() {
        val t = target ?: return
        mob.navigation.moveTo(t.x(), t.y(), t.z(), speed)
    }

    override fun stop() {
        mob.navigation.stop()
        targetManager.densityManager.stopGuarding(mob)
        target = null
    }
}

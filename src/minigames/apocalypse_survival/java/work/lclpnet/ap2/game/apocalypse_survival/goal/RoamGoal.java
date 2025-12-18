package work.lclpnet.ap2.game.apocalypse_survival.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.apocalypse_survival.util.TargetManager;

import java.util.EnumSet;

public class RoamGoal extends Goal {

    private final PathfinderMob mob;
    private final TargetManager targetManager;
    private final double speed;
    private @Nullable Vec3 target = null;

    public RoamGoal(PathfinderMob mob, TargetManager targetManager, double speed) {
        this.mob = mob;
        this.targetManager = targetManager;
        this.speed = speed;

        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.hasControllingPassenger() || mob.getTarget() != null || mob.getNavigation().isInProgress()) {
            return false;
        }

        target = targetManager.getDensityManager().startGuarding(mob);

        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.getNavigation().isDone() && !mob.hasControllingPassenger();

    }

    @Override
    public void start() {
        if (target == null) return;

        mob.getNavigation().moveTo(target.x(), target.y(), target.z(), speed);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        targetManager.getDensityManager().stopGuarding(mob);
        target = null;
    }
}

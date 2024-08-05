package work.lclpnet.ap2.game.apocalypse_survival.goal;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.apocalypse_survival.util.TargetManager;

import java.util.EnumSet;

public class GuardEmptyAreaGoal extends Goal {

    private final PathAwareEntity mob;
    private final TargetManager targetManager;
    private final double speed;
    private @Nullable Vec3d target = null;

    public GuardEmptyAreaGoal(PathAwareEntity mob, TargetManager targetManager, double speed) {
        this.mob = mob;
        this.targetManager = targetManager;
        this.speed = speed;

        setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (mob.hasControllingPassenger() || mob.getTarget() != null) {
            return false;
        }

        target = targetManager.getDensityManager().startGuarding(mob);

        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        return !mob.getNavigation().isIdle() && !mob.hasControllingPassenger();

    }

    @Override
    public void start() {
        if (target == null) return;

        targetManager.getScoreboardManager().joinTeam(mob, targetManager.getGuardTeam());
        mob.getNavigation().startMovingTo(target.getX(), target.getY(), target.getZ(), speed);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        targetManager.getDensityManager().stopGuarding(mob);
        target = null;

        targetManager.getScoreboardManager().leaveTeam(mob, targetManager.getGuardTeam());
    }
}

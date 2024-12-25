package work.lclpnet.ap2.game.maze_scape.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class MoveToTargetGoal extends Goal {

    protected final PathAwareEntity mob;
    protected final double speed;
    protected @Nullable Path path = null;
    protected @Nullable BlockPos targetPos = null, prevTargetPos = null;

    public MoveToTargetGoal(PathAwareEntity mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    protected @Nullable LivingEntity target() {
        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }

        return target;
    }

    @Override
    public boolean canStart() {
        LivingEntity target = target();

        if (target == null) return false;

        updatePath(target);

        return path != null;
    }

    @Override
    public void start() {
        prevTargetPos = null;
        startPathing();
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = target();

        if (target == null) return false;

        EntityNavigation nav = mob.getNavigation();

        if (nav.isIdle()) return false;

        updatePath(target);

        return this.path != null;
    }

    @Override
    public void tick() {
        startPathing();
    }

    private void updatePath(LivingEntity target) {
        prevTargetPos = targetPos;
        targetPos = target.getBlockPos();
        path = mob.getNavigation().findPathTo(target, 0);
    }

    private void startPathing() {
        if (path == null || prevTargetPos == targetPos) return;

        mob.getNavigation().startMovingAlong(path, speed);
        prevTargetPos = targetPos;
    }
}

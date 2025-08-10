package work.lclpnet.ap2.game.maze_scape.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Supplier;

public class MoveToTargetGoal extends Goal {

    protected final PathAwareEntity mob;
    protected final double speed;
    protected final Supplier<@Nullable BlockPos> targetSupplier;
    protected @Nullable Path path = null;
    protected @Nullable BlockPos targetPos = null, prevTargetPos = null;

    public MoveToTargetGoal(PathAwareEntity mob, double speed) {
        this(mob, speed, () -> targetEntityPos(mob));
    }

    public MoveToTargetGoal(PathAwareEntity mob, double speed, Supplier<@Nullable BlockPos> targetSupplier) {
        this.mob = mob;
        this.speed = speed;
        this.targetSupplier = targetSupplier;
        this.setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {
        BlockPos pos = targetSupplier.get();

        if (pos == null) return false;

        updatePath(pos);

        return path != null;
    }

    @Override
    public void start() {
        prevTargetPos = null;
        startPathing();
    }

    @Override
    public boolean shouldContinue() {
        BlockPos pos = targetSupplier.get();

        if (pos == null) return false;

        EntityNavigation nav = mob.getNavigation();

        if (nav.isIdle()) return false;

        updatePath(pos);

        return this.path != null;
    }

    @Override
    public void tick() {
        startPathing();
    }

    private void updatePath(BlockPos pos) {
        prevTargetPos = targetPos;
        targetPos = pos;
        path = mob.getNavigation().findPathTo(pos, 0);
    }

    private void startPathing() {
        if (path == null || prevTargetPos == targetPos) return;

        mob.getNavigation().startMovingAlong(path, speed);
        prevTargetPos = targetPos;
    }

    private static @Nullable BlockPos targetEntityPos(PathAwareEntity mob) {
        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }

        return target.getBlockPos();
    }
}

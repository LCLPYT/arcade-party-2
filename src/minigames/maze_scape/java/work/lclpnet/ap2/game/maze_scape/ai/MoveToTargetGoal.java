package work.lclpnet.ap2.game.maze_scape.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Supplier;

public class MoveToTargetGoal extends Goal {

    private static final int UPDATE_INTERVAL_TICKS = 5;

    protected final PathfinderMob mob;
    protected final double speed;
    protected final Supplier<@Nullable BlockPos> targetSupplier;
    protected @Nullable Path path = null;
    protected @Nullable BlockPos targetPos = null, prevTargetPos = null;
    private int updateTimer = 0;

    public MoveToTargetGoal(PathfinderMob mob, double speed) {
        this(mob, speed, () -> targetEntityPos(mob));
    }

    public MoveToTargetGoal(PathfinderMob mob, double speed, Supplier<@Nullable BlockPos> targetSupplier) {
        this.mob = mob;
        this.speed = speed;
        this.targetSupplier = targetSupplier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
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
    public boolean canContinueToUse() {
        BlockPos pos = targetSupplier.get();

        if (pos == null) return false;

        PathNavigation nav = mob.getNavigation();

        if (nav.isDone()) return false;

        if (pos.equals(targetPos) || updateTimer++ % UPDATE_INTERVAL_TICKS != 0) {
            return true;
        }

        updatePath(pos);

        return this.path != null;
    }

    @Override
    public void stop() {
        super.stop();
        updateTimer = 0;
    }

    @Override
    public void tick() {
        startPathing();
    }

    private void updatePath(BlockPos pos) {
        prevTargetPos = targetPos;
        targetPos = pos;
        path = mob.getNavigation().createPath(pos, 0);
    }

    private void startPathing() {
        if (path == null || prevTargetPos == targetPos) return;

        mob.getNavigation().moveTo(path, speed);
        prevTargetPos = targetPos;
    }

    private static @Nullable BlockPos targetEntityPos(PathfinderMob mob) {
        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }

        return target.blockPosition();
    }
}

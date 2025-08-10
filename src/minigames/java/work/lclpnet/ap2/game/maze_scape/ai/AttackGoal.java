package work.lclpnet.ap2.game.maze_scape.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class AttackGoal extends Goal {

    private static final int
            ATTACK_TIME_TICKS = 20,
            UPDATE_TICKS = 20;

    protected final PathAwareEntity mob;
    private int cooldown;
    private long lastUpdateTime;

    public AttackGoal(PathAwareEntity mob) {
        this.mob = mob;
        setControls(EnumSet.of(Control.LOOK));
    }

    protected @Nullable LivingEntity target() {
        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }

        return target;
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public boolean canStart() {
        long l = this.mob.getWorld().getTime();

        if (l - this.lastUpdateTime < UPDATE_TICKS) {
            return false;
        }

        this.lastUpdateTime = l;

        LivingEntity target = target();

        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = target();

        return target != null && (!(target instanceof ServerPlayerEntity player) || !player.isCreative());
    }

    @Override
    public void start() {
        mob.setAttacking(true);
        cooldown = 0;
    }

    @Override
    public void stop() {
        mob.setAttacking(false);
    }

    @Override
    public void tick() {
        LivingEntity target = target();

        if (target == null) return;

        mob.getLookControl().lookAt(target, 30.0f, 30.0f);

        cooldown = Math.max(cooldown - 1, 0);

        if (cooldown == 0 && mob.isInAttackRange(target) && mob.getVisibilityCache().canSee(target)) {
            cooldown = getTickCount(ATTACK_TIME_TICKS);
            mob.swingHand(Hand.MAIN_HAND);
            mob.tryAttack(getServerWorld(mob), target);
        }
    }
}

package work.lclpnet.ap2.game.maze_scape.ai;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class AttackGoal extends Goal {

    private static final int
            ATTACK_TIME_TICKS = 20,
            UPDATE_TICKS = 20;

    protected final PathfinderMob mob;
    private int cooldown;
    private long lastUpdateTime;

    public AttackGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    protected @Nullable LivingEntity target() {
        LivingEntity target = mob.getTarget();

        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }

        return target;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        long l = this.mob.level().getGameTime();

        if (l - this.lastUpdateTime < UPDATE_TICKS) {
            return false;
        }

        this.lastUpdateTime = l;

        LivingEntity target = target();

        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = target();

        return target != null && (!(target instanceof ServerPlayer player) || !player.isCreative());
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        cooldown = 0;
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
    }

    @Override
    public void tick() {
        LivingEntity target = target();

        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        cooldown = Math.max(cooldown - 1, 0);

        if (cooldown == 0 && mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
            cooldown = adjustedTickDelay(ATTACK_TIME_TICKS);
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(getServerLevel(mob), target);
        }
    }
}

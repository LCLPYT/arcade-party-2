package work.lclpnet.ap2.game.maze_scape.monster.behaviour;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import work.lclpnet.ap2.impl.util.EntityUtil;

import static net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED;

public class AccelerationBehaviour implements MonsterBehaviour {

    private static final double
            ACCELERATION_DISTANCE_SQ = 16 * 16,
            ACCELERATION_PER_TICK = 2.0E-4;

    private final double baseSpeed, maxSpeed;

    public AccelerationBehaviour(double baseSpeed, double maxSpeed) {
        this.baseSpeed = baseSpeed;
        this.maxSpeed = maxSpeed;
    }

    @Override
    public void init(MobEntity mob) {
        resetSpeed(mob);
    }

    @Override
    public void tick(MobEntity mob) {
        LivingEntity target = mob.getTarget();

        if (target == null || mob.squaredDistanceTo(target) > ACCELERATION_DISTANCE_SQ) return;

        double currentSpeed = mob.getAttributeBaseValue(MOVEMENT_SPEED);
        double newSpeed = Math.min(currentSpeed + ACCELERATION_PER_TICK, maxSpeed);

        EntityUtil.setAttribute(mob, MOVEMENT_SPEED, newSpeed);
    }

    @Override
    public void onKillAcquired(MobEntity mob) {
        resetSpeed(mob);
    }

    private void resetSpeed(MobEntity mob) {
        EntityUtil.setAttribute(mob, MOVEMENT_SPEED, baseSpeed);
    }
}

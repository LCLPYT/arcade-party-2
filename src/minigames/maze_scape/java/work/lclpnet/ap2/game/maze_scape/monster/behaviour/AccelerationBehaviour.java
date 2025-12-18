package work.lclpnet.ap2.game.maze_scape.monster.behaviour;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import work.lclpnet.ap2.impl.util.EntityUtil;

import static net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;

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
    public void init(Mob mob) {
        resetSpeed(mob);
    }

    @Override
    public void tick(Mob mob) {
        LivingEntity target = mob.getTarget();

        if (target == null || mob.distanceToSqr(target) > ACCELERATION_DISTANCE_SQ) return;

        double currentSpeed = mob.getAttributeBaseValue(MOVEMENT_SPEED);
        double newSpeed = Math.min(currentSpeed + ACCELERATION_PER_TICK, maxSpeed);

        EntityUtil.setAttribute(mob, MOVEMENT_SPEED, newSpeed);
    }

    @Override
    public void onKillAcquired(Mob mob) {
        resetSpeed(mob);
    }

    private void resetSpeed(Mob mob) {
        EntityUtil.setAttribute(mob, MOVEMENT_SPEED, baseSpeed);
    }
}

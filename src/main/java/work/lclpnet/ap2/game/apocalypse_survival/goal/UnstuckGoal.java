package work.lclpnet.ap2.game.apocalypse_survival.goal;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.access.VelocityModifier;

import java.util.Random;

public class UnstuckGoal extends Goal {

    private static final double TOLERANCE = 1.5 * 1.5;
    private static final int SCAN_TICKS = 50;
    private final MobEntity mob;
    private final Random random;
    private Vec3d lastPos = null;
    private int notMovedTicks = 0;
    private @Nullable Vec3d flingTarget = null;
    private int towardsTargetTicks = 0;

    public UnstuckGoal(MobEntity mob, Random random) {
        this.mob = mob;
        this.random = random;
    }

    @Override
    public boolean canStart() {
        return true;
    }

    @Override
    public void start() {
        lastPos = mob.getPos();
    }

    @Override
    public void tick() {
        Vec3d currentPos = mob.getPos();

        if (towardsTargetTicks > 0 && flingTarget != null) {
            towardsTargetTicks--;
            Vec3d vel = flingTarget.subtract(currentPos).normalize().multiply(0.6);
            VelocityModifier.setVelocity(mob, vel);
            return;
        }

        if (lastPos.squaredDistanceTo(currentPos) > TOLERANCE) {
            lastPos = currentPos;
            notMovedTicks = 0;
            return;
        }

        if (++notMovedTicks >= SCAN_TICKS) {
            notMovedTicks = 0;
            unstuck();
        }
    }

    private void unstuck() {
        PlayerEntity nearbyPlayer = mob.getWorld().getClosestPlayer(mob, 10);

        if (nearbyPlayer != null) {
            flingTarget = nearbyPlayer.getEyePos();
            towardsTargetTicks = 4;
            return;
        }

        float pitch = -45 - random.nextFloat() * 25;
        float yaw = random.nextFloat() * 360;

        Vec3d direction = Vec3d.fromPolar(pitch, yaw);

        VelocityModifier.setVelocity(mob, direction.multiply(0.6));
    }
}

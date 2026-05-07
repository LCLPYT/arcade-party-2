package work.lclpnet.ap2.game.apocalypse_survival.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.access.VelocityModifier;

import java.util.Random;

public class UnstuckGoal extends Goal {

    private static final double TOLERANCE = 1.5 * 1.5;
    private static final int SCAN_TICKS = 50;
    private final Mob mob;
    private final Random random;
    private Vec3 lastPos = null;
    private int notMovedTicks = 0;
    private @Nullable Vec3 flingTarget = null;
    private int towardsTargetTicks = 0;

    public UnstuckGoal(Mob mob, Random random) {
        this.mob = mob;
        this.random = random;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public void start() {
        lastPos = mob.position();
    }

    @Override
    public void tick() {
        Vec3 currentPos = mob.position();

        if (towardsTargetTicks > 0 && flingTarget != null) {
            towardsTargetTicks--;
            Vec3 vel = flingTarget.subtract(currentPos).normalize().scale(0.6);
            VelocityModifier.setVelocity(mob, vel);
            return;
        }

        if (lastPos.distanceToSqr(currentPos) > TOLERANCE) {
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
        destroyBlockage();
        destroyHideout();

        Player nearbyPlayer = mob.level().getNearestPlayer(mob, 10);

        if (nearbyPlayer != null) {
            flingTarget = nearbyPlayer.getEyePosition();
            towardsTargetTicks = 4;
            return;
        }

        float pitch = -45 - random.nextFloat() * 25;
        float yaw = random.nextFloat() * 360;

        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);

        VelocityModifier.setVelocity(mob, direction.scale(0.6));
    }

    private void destroyHideout() {
        LivingEntity target = mob.getTarget();

        if (target == null || mob.distanceToSqr(target) > TOLERANCE) return;

        // target in reach, check if it is hiding below a trapdoor
        BlockPos aboveTarget = target.blockPosition().above();

        Level world = mob.level();
        BlockState state = world.getBlockState(aboveTarget);

        if (state.is(BlockTags.WOODEN_TRAPDOORS)) {
            world.destroyBlock(aboveTarget, false, mob);

            world.playSound(null, aboveTarget.getX() + 0.5, aboveTarget.getY() + 0.5, aboveTarget.getZ() + 0.5,
                    SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.75f, 1f);
        }
    }

    private void destroyBlockage() {
        Level world = mob.level();

        BlockPos.betweenClosedStream(mob.getBoundingBox())
                .filter(pos -> world.getBlockState(pos).is(Blocks.COBWEB))
                .forEach(pos -> world.destroyBlock(pos, false, mob));
    }
}

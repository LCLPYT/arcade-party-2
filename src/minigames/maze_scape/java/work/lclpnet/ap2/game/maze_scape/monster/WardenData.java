package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.AccelerationBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.SameRoomBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.UnstuckBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.ValidPositionBehaviour;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.List;

import static net.minecraft.entity.attribute.EntityAttributes.KNOCKBACK_RESISTANCE;

public class WardenData implements MonsterData<WardenEntity> {

    private static final int
            SONIC_BOOM_TRIGGER_TICKS = Ticks.seconds(18),
            SONIC_BOOM_SOUND_TICKS = 34;

    private final CommonData common;
    private int sonicBoomSoundDelay = 0;
    private @Nullable LivingEntity sonicBoomTarget = null;

    public WardenData(MonsterArgs args) {
        this.common = new CommonData(args, List.of(
                new ValidPositionBehaviour(args.manager(), args.logger()),
                new AccelerationBehaviour(0.3, 0.45),
                new UnstuckBehaviour(args.manager(), 0.75),
                new SameRoomBehaviour<>(args.manager().struct(), SONIC_BOOM_TRIGGER_TICKS, this::triggerSonicBoom)
        ));
    }

    @Override
    public void init(WardenEntity mob) {
        common.init(mob);
    }

    @Override
    public void tick(WardenEntity warden) {
        common.tick(warden);

        if (sonicBoomTarget != null) {
            if (sonicBoomTarget.isAlive()) {
                if (sonicBoomSoundDelay++ >= SONIC_BOOM_SOUND_TICKS) {
                    fireSonicBoom(warden, sonicBoomTarget);
                    sonicBoomTarget = null;
                    sonicBoomSoundDelay = 0;
                }
            } else {
                sonicBoomTarget = null;
                sonicBoomSoundDelay = 0;
            }
        }
    }

    @Override
    public void onKillAcquired(WardenEntity mob) {
        common.onKillAcquired(mob);
    }

    private void triggerSonicBoom(WardenEntity warden, LivingEntity target) {
        sonicBoomTarget = target;
        common.manager().world().sendEntityStatus(warden, EntityStatuses.SONIC_BOOM);
        warden.playSound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 3.0f, 1.0f);
    }

    private void fireSonicBoom(WardenEntity warden, LivingEntity target) {
        Vec3d chest = warden.getPos().add(warden.getAttachments().getPoint(EntityAttachmentType.WARDEN_CHEST, 0, warden.getYaw()));
        Vec3d line = target.getEyePos().subtract(chest);
        Vec3d dir = line.normalize();

        ServerWorld world = common.manager().world();

        int i = MathHelper.floor(line.length()) + 7;

        for (int j = 1; j < i; ++j) {
            Vec3d pos = chest.add(dir.multiply(j));
            world.spawnParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        warden.playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);

        if (target.damage(world, world.getDamageSources().sonicBoom(warden), 10.0f)) {
            double vertical = 0.5 * (1.0 - target.getAttributeValue(KNOCKBACK_RESISTANCE));
            double horizontal = 2.5 * (1.0 - target.getAttributeValue(KNOCKBACK_RESISTANCE));

            target.addVelocity(dir.getX() * horizontal, dir.getY() * vertical, dir.getZ() * horizontal);
        }
    }

    @Override
    public @Nullable WardenEntity mob() {
        if (common.mob() instanceof WardenEntity warden) {
            return warden;
        }

        return null;
    }
}

package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.UUID;

public class WardenData implements MonsterData {

    private static final int
            SONIC_BOOM_TRIGGER_TICKS = Ticks.seconds(3),
            SONIC_BOOM_SOUND_TICKS = 34;

    private final CommonData common;
    private int sonicBoomTimer = 0;
    private int sonicBoomSoundDelay = 0;
    private @Nullable LivingEntity sonicBoomTarget = null;

    public WardenData(UUID uuid, MSManager manager, Logger logger) {
        this.common = new CommonData(uuid, manager, logger);
    }

    @Override
    public void tick() {
        common.tick();

        WardenEntity warden = warden();

        if (warden == null) return;

        LivingEntity target = warden.getTarget();

        if (common.stuckTimer() > 0 && target != null && inRangeForSonicBoom(warden, target)) {
            if (sonicBoomTimer++ >= SONIC_BOOM_TRIGGER_TICKS) {
                triggerSonicBoom(target, warden);
                sonicBoomTimer = 0;
            }
        } else {
            sonicBoomTimer = 0;
        }

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

    private boolean inRangeForSonicBoom(WardenEntity warden, LivingEntity target) {
        MSStruct struct = common.manager().struct();

        var wardenNode = struct.nodeAt(warden.getPos());
        var targetNode = struct.nodeAt(target.getPos());

        return wardenNode != null && wardenNode == targetNode;
    }

    private void triggerSonicBoom(LivingEntity target, WardenEntity warden) {
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

        if (target.damage(world.getDamageSources().sonicBoom(warden), 10.0f)) {
            double vertical = 0.5 * (1.0 - target.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
            double horizontal = 2.5 * (1.0 - target.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));

            target.addVelocity(dir.getX() * horizontal, dir.getY() * vertical, dir.getZ() * horizontal);
        }
    }

    private @Nullable WardenEntity warden() {
        if (common.mob() instanceof WardenEntity warden) {
            return warden;
        }

        return null;
    }
}

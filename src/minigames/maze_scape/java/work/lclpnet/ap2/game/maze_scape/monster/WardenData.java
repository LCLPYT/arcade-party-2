package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.AccelerationBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.SameRoomBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.UnstuckBehaviour;
import work.lclpnet.ap2.game.maze_scape.monster.behaviour.ValidPositionBehaviour;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.List;

import static net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE;

public class WardenData implements MonsterData<Warden> {

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
    public void init(Warden mob) {
        common.init(mob);
    }

    @Override
    public void tick(Warden warden) {
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
    public void onKillAcquired(Warden mob) {
        common.onKillAcquired(mob);
    }

    private void triggerSonicBoom(Warden warden, LivingEntity target) {
        sonicBoomTarget = target;
        common.manager().world().broadcastEntityEvent(warden, EntityEvent.SONIC_CHARGE);
        warden.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0f, 1.0f);
    }

    private void fireSonicBoom(Warden warden, LivingEntity target) {
        Vec3 chest = warden.position().add(warden.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, warden.getYRot()));
        Vec3 line = target.getEyePosition().subtract(chest);
        Vec3 dir = line.normalize();

        ServerLevel world = common.manager().world();

        int i = Mth.floor(line.length()) + 7;

        for (int j = 1; j < i; ++j) {
            Vec3 pos = chest.add(dir.scale(j));
            world.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        warden.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 1.0f);

        if (target.hurtServer(world, world.damageSources().sonicBoom(warden), 10.0f)) {
            double vertical = 0.5 * (1.0 - target.getAttributeValue(KNOCKBACK_RESISTANCE));
            double horizontal = 2.5 * (1.0 - target.getAttributeValue(KNOCKBACK_RESISTANCE));

            target.push(dir.x() * horizontal, dir.y() * vertical, dir.z() * horizontal);
        }
    }

    @Override
    public @Nullable Warden mob() {
        if (common.mob() instanceof Warden warden) {
            return warden;
        }

        return null;
    }
}

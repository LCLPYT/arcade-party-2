package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.impl.util.world.stage.Stage;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Random;

public class MonsterSpawner {

    private static final int
            PARTICLE_TICKS = 12,
            MOB_MIN_TICKS = Ticks.seconds(1),
            MOB_MAX_TICKS = Ticks.seconds(4),
            MOB_LIMIT = 100;
    private final ServerWorld world;
    private final Stage stage;
    private final Random random;
    private int nextParticle = 0;
    private int nextMob;
    private int mobCount = 0;

    public MonsterSpawner(ServerWorld world, Stage stage, Random random) {
        this.world = world;
        this.stage = stage;
        this.random = random;

        scheduleNextMob();
    }

    public void tick() {
        if (nextParticle-- <= 0) {
            nextParticle = PARTICLE_TICKS;
            spawnParticle();
        }

        if (mobCount < MOB_LIMIT && nextMob-- <= 0) {
            scheduleNextMob();
            spawnMob();
        }
    }

    private void scheduleNextMob() {
        this.nextMob = MOB_MIN_TICKS + random.nextInt(MOB_MAX_TICKS - MOB_MIN_TICKS + 1);
    }

    private void spawnParticle() {
        BlockPos center = stage.getCenter();
        int offset = stage.getRadius() / 2;

        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                30, offset, offset, offset, 0.15);
    }

    private void spawnMob() {
        BlockPos pos = stage.getOrigin();

        ZombieEntity zombie = new ZombieEntity(EntityType.ZOMBIE, world);
        zombie.setPersistent();
        zombie.setPosition(Vec3d.ofBottomCenter(pos));

        var followRange = zombie.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);

        if (followRange != null) {
            followRange.setBaseValue(100);
        }

        world.spawnEntity(zombie);
        mobCount++;
    }
}

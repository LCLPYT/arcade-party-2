package work.lclpnet.ap2.impl.util.world;

import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.particle.BlockParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.Optional;

public class ExplosionUtil {

    public static final Pool<BlockParticleEffect> EXPLOSION_BLOCK_PARTICLES = Pool.<BlockParticleEffect>builder()
            .add(new BlockParticleEffect(ParticleTypes.POOF, 0.5F, 1.0F))
            .add(new BlockParticleEffect(ParticleTypes.SMOKE, 1.0F, 1.0F))
            .build();

    public static void sendExplosion(ServerWorld world, ExplosionImpl explosion) {
        ParticleEffect particleEffect = explosion.isSmall() ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER;

        sendExplosion(world, explosion, particleEffect);
    }

    public static void sendExplosion(ServerWorld world, ExplosionImpl explosion, ParticleEffect particleEffect) {
        sendExplosion(world, explosion, particleEffect, EXPLOSION_BLOCK_PARTICLES);
    }

    public static void sendExplosion(ServerWorld world, ExplosionImpl explosion, ParticleEffect particleEffect, Pool<BlockParticleEffect> blockParticles) {
        Vec3d pos = explosion.getPosition();

        // send explosion packets
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (!(other.squaredDistanceTo(pos.x, pos.y, pos.z) < 4096.0)) continue;

            Optional<Vec3d> knockback = Optional.ofNullable(explosion.getKnockbackByPlayer().get(other));

            other.networkHandler.sendPacket(new ExplosionS2CPacket(pos, explosion.getPower(), 0, knockback, particleEffect, SoundEvents.ENTITY_GENERIC_EXPLODE, blockParticles));
        }
    }
}

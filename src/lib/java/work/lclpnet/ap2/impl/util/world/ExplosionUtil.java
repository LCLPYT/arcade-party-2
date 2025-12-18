package work.lclpnet.ap2.impl.util.world;

import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class ExplosionUtil {

    public static final WeightedList<ExplosionParticleInfo> EXPLOSION_BLOCK_PARTICLES = WeightedList.<ExplosionParticleInfo>builder()
            .add(new ExplosionParticleInfo(ParticleTypes.POOF, 0.5F, 1.0F))
            .add(new ExplosionParticleInfo(ParticleTypes.SMOKE, 1.0F, 1.0F))
            .build();

    public static void sendExplosion(ServerLevel world, ServerExplosion explosion) {
        ParticleOptions particleEffect = explosion.isSmall() ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER;

        sendExplosion(world, explosion, particleEffect);
    }

    public static void sendExplosion(ServerLevel world, ServerExplosion explosion, ParticleOptions particleEffect) {
        sendExplosion(world, explosion, particleEffect, EXPLOSION_BLOCK_PARTICLES);
    }

    public static void sendExplosion(ServerLevel world, ServerExplosion explosion, ParticleOptions particleEffect, WeightedList<ExplosionParticleInfo> blockParticles) {
        Vec3 pos = explosion.center();

        // send explosion packets
        for (ServerPlayer other : world.players()) {
            if (!(other.distanceToSqr(pos.x, pos.y, pos.z) < 4096.0)) continue;

            Optional<Vec3> knockback = Optional.ofNullable(explosion.getHitPlayers().get(other));

            other.connection.send(new ClientboundExplodePacket(pos, explosion.radius(), 0, knockback, particleEffect, SoundEvents.GENERIC_EXPLODE, blockParticles));
        }
    }
}

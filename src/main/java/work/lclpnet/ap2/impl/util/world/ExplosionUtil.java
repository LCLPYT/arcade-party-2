package work.lclpnet.ap2.impl.util.world;

import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.Optional;

public class ExplosionUtil {

    public static void sendExplosion(ServerWorld world, ExplosionImpl explosion) {
        Vec3d pos = explosion.getPosition();

        // send explosion packets
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (!(other.squaredDistanceTo(pos.x, pos.y, pos.z) < 4096.0)) continue;

            var knockback = Optional.ofNullable(explosion.getKnockbackByPlayer().get(other));

            other.networkHandler.sendPacket(new ExplosionS2CPacket(pos, knockback, ParticleTypes.EXPLOSION, SoundEvents.ENTITY_GENERIC_EXPLODE));
        }
    }
}

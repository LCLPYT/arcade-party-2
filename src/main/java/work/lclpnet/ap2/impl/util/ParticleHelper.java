package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class ParticleHelper {

    private ParticleHelper() {}

    public static <T extends ParticleEffect> void spawnForceParticle(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed, Iterable<? extends ServerPlayerEntity> players) {
        ParticleS2CPacket packet = new ParticleS2CPacket(particle, true, false, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count);

        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(packet);
        }
    }

    public static <T extends ParticleEffect> void spawnParticleAt(Entity entity, T particle, int count, double dx, double dy, double dz, double speed) {
        if (!(entity.getWorld() instanceof ServerWorld world)) return;

        world.spawnParticles(particle, entity.getX(), entity.getY(), entity.getZ(), count, dx, dy, dz, speed);
    }
}

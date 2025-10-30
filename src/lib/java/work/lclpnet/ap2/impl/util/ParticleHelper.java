package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class ParticleHelper {

    private ParticleHelper() {}

    public static <T extends ParticleEffect> void spawnForceParticle(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed, Iterable<? extends ServerPlayerEntity> players) {
        spawnParticleFor(particle, x, y, z, count, dx, dy, dz, speed, true, false, players);
    }

    public static <T extends ParticleEffect> void spawnParticleFor(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed, Iterable<? extends ServerPlayerEntity> players) {
        spawnParticleFor(particle, x, y, z, count, dx, dy, dz, speed, false, false, players);
    }

    public static <T extends ParticleEffect> void spawnParticleFor(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed, boolean force, boolean important, Iterable<? extends ServerPlayerEntity> players) {
        ParticleS2CPacket packet = new ParticleS2CPacket(particle, force, important, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count);

        double range = force ? 512 : 32;
        double rangeSq = range * range;

        for (ServerPlayerEntity player : players) {
            if (player.squaredDistanceTo(x, y, z) <= rangeSq) {
                player.networkHandler.sendPacket(packet);
            }
        }
    }

    public static <T extends ParticleEffect> void spawnParticleAt(Entity entity, T particle, int count, double dx, double dy, double dz, double speed) {
        if (!(entity.getEntityWorld() instanceof ServerWorld world)) return;

        world.spawnParticles(particle, entity.getX(), entity.getY(), entity.getZ(), count, dx, dy, dz, speed);
    }
}

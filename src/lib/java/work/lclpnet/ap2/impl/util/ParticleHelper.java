package work.lclpnet.ap2.impl.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class ParticleHelper {

    private ParticleHelper() {}

    public static <T extends ParticleOptions> void spawnForceParticle(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed, Iterable<? extends ServerPlayer> players) {
        spawnParticleFor(particle, x, y, z, count, dx, dy, dz, speed, true, false, players);
    }

    public static <T extends ParticleOptions> void spawnParticleFor(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed, Iterable<? extends ServerPlayer> players) {
        spawnParticleFor(particle, x, y, z, count, dx, dy, dz, speed, false, false, players);
    }

    public static <T extends ParticleOptions> void spawnParticleFor(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed, boolean force, boolean important, Iterable<? extends ServerPlayer> players) {
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(particle, force, important, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count);

        double range = force ? 512 : 32;
        double rangeSq = range * range;

        for (ServerPlayer player : players) {
            if (player.distanceToSqr(x, y, z) <= rangeSq) {
                player.connection.send(packet);
            }
        }
    }

    public static <T extends ParticleOptions> void spawnParticleAt(Entity entity, T particle, int count, double dx, double dy, double dz, double speed) {
        if (!(entity.level() instanceof ServerLevel world)) return;

        world.sendParticles(particle, entity.getX(), entity.getY(), entity.getZ(), count, dx, dy, dz, speed);
    }
}

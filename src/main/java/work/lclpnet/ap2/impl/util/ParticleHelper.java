package work.lclpnet.ap2.impl.util;

import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;

public class ParticleHelper {

    private ParticleHelper() {}

    public static <T extends ParticleEffect> void spawnForceParticle(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed, Iterable<? extends ServerPlayerEntity> players) {
        ParticleS2CPacket packet = new ParticleS2CPacket(particle, true, x, y, z, (float) dx, (float) dy, (float) dz, (float) speed, count);

        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(packet);
        }
    }
}

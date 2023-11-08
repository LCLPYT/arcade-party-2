package work.lclpnet.ap2.impl.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

public class SoundHelper {

    public static void playSound(MinecraftServer server, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            player.playSound(sound, category, volume, pitch);
        }
    }

    public static void playSound(ServerPlayerEntity player, SoundEvent sound, SoundCategory category,
                                 double x, double y, double z, float volume, float pitch) {

        var entry = Registries.SOUND_EVENT.getEntry(sound);
        long seed = player.getRandom().nextLong();
        var packet = new PlaySoundS2CPacket(entry, category, x, y, z, volume, pitch, seed);

        player.networkHandler.sendPacket(packet);
    }

    private SoundHelper() {}
}

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
            player.playSoundToPlayer(sound, category, volume, pitch);
        }
    }

    public static void playSound(ServerPlayerEntity player, SoundEvent sound, SoundCategory category,
                                 double x, double y, double z, float volume, float pitch) {

        var entry = Registries.SOUND_EVENT.getEntry(sound);
        long seed = player.getRandom().nextLong();
        var packet = new PlaySoundS2CPacket(entry, category, x, y, z, volume, pitch, seed);

        player.networkHandler.sendPacket(packet);
    }

    /**
     * Get the note pitch for a given note key.
     * @param key The note key, ranging from F#3 (0) to F#5 (24), where one octave is 12 keys.
     * @return The Minecraft sound pitch for the given key.
     */
    public static float getPitch(int key) {
        float pitch = (float) Math.pow(2, (key - 12) / 12f);
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    private SoundHelper() {}
}

package work.lclpnet.ap2.impl.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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

    private SoundHelper() {}
}

package work.lclpnet.ap2.impl.util;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;

import static java.lang.Math.max;

public class SoundHelper {

    public static void playSound(MinecraftServer server, SoundEvent sound, SoundSource category, float volume, float pitch) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            ServerPlayerAccess.playSoundToPlayer(player, sound, category, volume, pitch);
        }
    }

    public static void playSound(ServerLevel world, SoundEvent sound, SoundSource category, float volume, float pitch) {
        for (ServerPlayer player : PlayerLookup.world(world)) {
            ServerPlayerAccess.playSoundToPlayer(player, sound, category, volume, pitch);
        }
    }

    public static void playSound(ServerPlayer player, SoundEvent sound, SoundSource category, Position pos,
                                 float volume, float pitch) {
        playSound(player, sound, category, pos.x(), pos.y(), pos.z(), volume, pitch);
    }

    public static void playSound(ServerPlayer player, SoundEvent sound, SoundSource category,
                                 double x, double y, double z, float volume, float pitch) {

        var entry = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        long seed = player.getRandom().nextLong();
        var packet = new ClientboundSoundPacket(entry, category, x, y, z, volume, pitch, seed);

        player.connection.send(packet);
    }

    public static void playSoundAt(Entity entity, SoundEvent sound, SoundSource category, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, category, volume, pitch);
    }

    public static void playSoundFor(SoundEvent sound, SoundSource category, Position pos, float volume, float pitch,
                                    Iterable<? extends ServerPlayer> players) {
        playSoundFor(sound, category, pos.x(), pos.y(), pos.z(), volume, pitch, players);
    }

    public static void playSoundFor(SoundEvent sound, SoundSource category, double x, double y, double z,
                                    float volume, float pitch, Iterable<? extends ServerPlayer> players) {
        double range = max(1.0, volume) * 16;
        double rangeSq = range * range;

        for (ServerPlayer player : players) {
            if (player.distanceToSqr(x, y, z) <= rangeSq) {
                playSound(player, sound, category, x, y, z, volume, pitch);
            }
        }
    }

    /**
     * Get the note pitch for a given note key.
     * @param key The note key, ranging from F#3 (0) to F#5 (24), where one octave is 12 keys.
     * @return The Minecraft sound pitch for the given key.
     */
    public static float getPitch(int key) {
        float pitch = (float) Math.pow(2, (key - 12) / 12f);
        return max(0.5f, Math.min(2.0f, pitch));
    }

    private SoundHelper() {}
}

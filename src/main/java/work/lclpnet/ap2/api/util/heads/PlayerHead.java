package work.lclpnet.ap2.api.util.heads;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public record PlayerHead(UUID uuid, String textureId, String texture) {

    public PlayerHead(UUID uuid, String textureId) {
        this(uuid, textureId, getBase64Texture(textureId));
    }

    public static final Codec<PlayerHead> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.INT_STREAM_CODEC.fieldOf("uuid").forGetter(PlayerHead::uuid),
            Codec.STRING.fieldOf("texture").forGetter(PlayerHead::textureId)
    ).apply(instance, PlayerHead::new));

    public static String getBase64Texture(String textureId) {
        @SuppressWarnings("HttpUrlsUsage")
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}".formatted(textureId);

        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}

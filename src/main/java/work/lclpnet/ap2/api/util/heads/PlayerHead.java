package work.lclpnet.ap2.api.util.heads;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import work.lclpnet.ap2.impl.util.UUIDUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public record PlayerHead(UUID uuid, String texture) {

    public static final Codec<PlayerHead> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.INT_STREAM_CODEC.fieldOf("uuid").forGetter(PlayerHead::uuid),
            Codec.STRING.fieldOf("texture").forGetter(PlayerHead::texture)
    ).apply(instance, PlayerHead::new));

    public static PlayerHead fromBase64(int mostSig1, int mostSig2, int leastSig1, int leastSig2, String texture) {
        UUID uuid = UUIDUtil.getUuid(mostSig1, mostSig2, leastSig1, leastSig2);
        return new PlayerHead(uuid, texture);
    }

    public static PlayerHead fromId(int mostSig1, int mostSig2, int leastSig1, int leastSig2, String textureId) {
        @SuppressWarnings("HttpUrlsUsage")
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}".formatted(textureId);
        String texture = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        return fromBase64(mostSig1, mostSig2, leastSig1, leastSig2, texture);
    }

    public static PlayerHead fromJson(JSONObject json) throws JSONException {
        JSONArray tuple = json.getJSONArray("uuid");
        String texture = json.getString("texture");

        int mostSig1 = tuple.getInt(0);
        int mostSig2 = tuple.getInt(1);
        int leastSig1 = tuple.getInt(2);
        int leastSig2 = tuple.getInt(3);

        return fromId(mostSig1, mostSig2, leastSig1, leastSig2, texture);
    }
}

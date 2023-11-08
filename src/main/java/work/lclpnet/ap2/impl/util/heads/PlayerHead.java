package work.lclpnet.ap2.impl.util.heads;

import work.lclpnet.ap2.impl.util.UUIDUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public record PlayerHead(UUID uuid, String texture) {

    public static PlayerHead fromBase64(int mostSig1, int mostSig2, int leastSig1, int leastSig2, String texture) {
        UUID uuid = UUIDUtil.getUuid(mostSig1, mostSig2, leastSig1, leastSig2);
        return new PlayerHead(uuid, texture);
    }

    public static PlayerHead fromId(int mostSig1, int mostSig2, int leastSig1, int leastSig2, String id) {
        @SuppressWarnings("HttpUrlsUsage")
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/%s\"}}}".formatted(id);
        String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        return fromBase64(mostSig1, mostSig2, leastSig1, leastSig2, base64);
    }
}

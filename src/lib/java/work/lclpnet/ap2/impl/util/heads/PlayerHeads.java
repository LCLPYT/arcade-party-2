package work.lclpnet.ap2.impl.util.heads;

import net.minecraft.resources.ResourceKey;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.impl.util.ApRegistries;

import static work.lclpnet.ap2.ApConstants.identifier;

public class PlayerHeads {

    public static final ResourceKey<PlayerHead>
            GEODE_ARROW_FORWARD = ref("geode_arrow_forward"),
            REDSTONE_BLOCK_REFRESH = ref("redstone_block_refresh"),
            EASTER_EGG_PINK_PATTERN = ref("easter_egg_pink_pattern");

    private PlayerHeads() {}

    private static ResourceKey<PlayerHead> ref(String path) {
        return ResourceKey.create(ApRegistries.PLAYER_HEAD, identifier(path));
    }
}

package work.lclpnet.ap2.impl.util.heads;

import net.minecraft.registry.RegistryKey;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.impl.util.ApRegistries;

import static work.lclpnet.ap2.base.ArcadeParty.identifier;

public class PlayerHeads {

    public static final RegistryKey<PlayerHead>
            GEODE_ARROW_FORWARD = ref("geode_arrow_forward"),
            REDSTONE_BLOCK_REFRESH = ref("redstone_block_refresh");

    private PlayerHeads() {}

    private static RegistryKey<PlayerHead> ref(String path) {
        return RegistryKey.of(ApRegistries.PLAYER_HEAD, identifier(path));
    }
}

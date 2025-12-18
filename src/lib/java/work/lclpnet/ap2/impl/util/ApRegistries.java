package work.lclpnet.ap2.impl.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import work.lclpnet.ap2.api.util.heads.PlayerHead;

import static work.lclpnet.ap2.ApConstants.identifier;

public class ApRegistries {

    public static final ResourceKey<Registry<PlayerHead>>
            PLAYER_HEAD = ResourceKey.createRegistryKey(identifier("player_head"));

    private ApRegistries() {}
}

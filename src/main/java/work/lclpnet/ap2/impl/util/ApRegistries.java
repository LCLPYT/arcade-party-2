package work.lclpnet.ap2.impl.util;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import work.lclpnet.ap2.api.util.heads.PlayerHead;

import static work.lclpnet.ap2.base.ArcadeParty.identifier;

public class ApRegistries {

    public static final RegistryKey<Registry<PlayerHead>>
            PLAYER_HEAD = RegistryKey.ofRegistry(identifier("player_head"));

    private ApRegistries() {}
}

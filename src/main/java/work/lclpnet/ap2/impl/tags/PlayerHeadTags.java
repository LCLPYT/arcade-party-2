package work.lclpnet.ap2.impl.tags;

import net.minecraft.registry.tag.TagKey;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.impl.util.ApRegistries;

import static work.lclpnet.ap2.base.ArcadeParty.identifier;

public class PlayerHeadTags {

    public static final TagKey<PlayerHead>
            EASTER_EGGS = of("easter_eggs");

    private PlayerHeadTags() {}

    private static TagKey<PlayerHead> of(String path) {
        return TagKey.of(ApRegistries.PLAYER_HEAD, identifier(path));
    }
}

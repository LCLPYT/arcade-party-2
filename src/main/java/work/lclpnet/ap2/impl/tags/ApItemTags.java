package work.lclpnet.ap2.impl.tags;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import work.lclpnet.ap2.base.ArcadeParty;

public class ApItemTags {

    public static TagKey<Item> DYES = of("dyes");
    public static TagKey<Item> BANNER_PATTERNS = of("banner_patterns");

    private static TagKey<Item> of(String path) {
        return TagKey.of(RegistryKeys.ITEM, ArcadeParty.identifier(path));
    }

    private ApItemTags() {}
}

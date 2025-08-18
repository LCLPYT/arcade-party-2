package work.lclpnet.ap2.impl.tags;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import work.lclpnet.ap2.ApConstants;

public class ApItemTags {

    public static TagKey<Item>
            DYES = of("dyes"),
            BANNER_PATTERNS = of("banner_patterns"),
            FLOWERS = of("flowers"),
            TRIM_TEMPLATES = of("trim_templates");

    private static TagKey<Item> of(String path) {
        return TagKey.of(RegistryKeys.ITEM, ApConstants.identifier(path));
    }

    private ApItemTags() {}
}

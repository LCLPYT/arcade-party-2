package work.lclpnet.ap2.impl.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import work.lclpnet.ap2.ApConstants;

public class ApItemTags {

    public static TagKey<Item>
            DYES = of("dyes"),
            BANNER_PATTERNS = of("banner_patterns"),
            FLOWERS = of("flowers"),
            TRIM_TEMPLATES = of("trim_templates");

    private static TagKey<Item> of(String path) {
        return TagKey.create(Registries.ITEM, ApConstants.identifier(path));
    }

    private ApItemTags() {}
}

package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.pillar_battle.item.*;
import work.lclpnet.ap2.impl.tags.ApItemTags;
import work.lclpnet.ap2.impl.util.IndexedSet;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class PbRandomizer {

    private final Random random;
    private final Participants participants;
    private final DynamicRegistryManager registryManager;
    private final IndexedSet<ItemClass> itemsClasses = new IndexedSet<>();

    public PbRandomizer(Random random, Participants participants, DynamicRegistryManager registryManager) {
        this.random = random;
        this.participants = participants;
        this.registryManager = registryManager;

        initItems();
    }

    private void initItems() {
        // group some items in a shared item class
        group(ItemTags.BANNERS);
        group(ItemTags.BEDS);
        group(ItemTags.CANDLES);
        group(ItemTags.DECORATED_POT_SHERDS);
        group(ItemTags.TRIM_TEMPLATES);
        group(ItemTags.SMALL_FLOWERS);
        group(ItemTags.TALL_FLOWERS);
        group(ApItemTags.DYES);
        group(ApItemTags.BANNER_PATTERNS);

        // potion items
        itemsClasses.add(new PotionItemClass(Items.POTION));
        itemsClasses.add(new PotionItemClass(Items.SPLASH_POTION));
        itemsClasses.add(new PotionItemClass(Items.LINGERING_POTION));
        itemsClasses.add(new PotionItemClass(Items.TIPPED_ARROW));

        // special classes
        itemsClasses.add(new EnchantedBookItemClass(registryManager));

        // add remaining items as singletons
        Set<Item> exclude = itemsClasses.stream()
                .flatMap(ItemClass::stream)
                .collect(Collectors.toSet());

        Registries.ITEM.stream()
                .filter(item -> !exclude.contains(item))
                .map(SingletonItemClass::new)
                .forEach(itemsClasses::add);
    }

    private void group(TagKey<Item> tag) {
        var itemClass = MultiItemClass.ofTag(tag);

        if (itemClass == null) return;

        itemsClasses.add(itemClass);
    }

    public void giveRandomItems() {
        for (ServerPlayerEntity player : participants) {
            giveRandomItem(player);
        }
    }

    private void giveRandomItem(ServerPlayerEntity player) {
        ItemStack stack = getRandomStack();

        player.giveItemStack(stack);
    }

    private ItemStack getRandomStack() {
        var itemClass = itemsClasses.get(random.nextInt(itemsClasses.size()));

        return itemClass.getRandomStack(random);
    }
}

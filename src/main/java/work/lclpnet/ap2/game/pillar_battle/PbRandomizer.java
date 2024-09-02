package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.pillar_battle.item.ItemClass;
import work.lclpnet.ap2.game.pillar_battle.item.MultiItemClass;
import work.lclpnet.ap2.game.pillar_battle.item.SingletonItemClass;
import work.lclpnet.ap2.impl.tags.ApItemTags;
import work.lclpnet.ap2.impl.util.IndexedSet;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class PbRandomizer {

    private final Random random;
    private final Participants participants;
    private final IndexedSet<ItemClass> itemsClasses = new IndexedSet<>();

    public PbRandomizer(Random random, Participants participants) {
        this.random = random;
        this.participants = participants;

        initItems();
    }

    private void initItems() {
        // collect all items in the game
        Set<Item> remaining = new HashSet<>();

        for (Item item : Registries.ITEM) {
            remaining.add(item);
        }

        // group some items in a shared item class
        group(ItemTags.BANNERS, remaining);
        group(ItemTags.BEDS, remaining);
        group(ItemTags.CANDLES, remaining);
        group(ItemTags.DECORATED_POT_SHERDS, remaining);
        group(ItemTags.TRIM_TEMPLATES, remaining);
        group(ItemTags.SMALL_FLOWERS, remaining);
        group(ItemTags.TALL_FLOWERS, remaining);
        group(ApItemTags.DYES, remaining);
        group(ApItemTags.BANNER_PATTERNS, remaining);

        // add remaining items as singletons
        remaining.stream()
                .map(SingletonItemClass::new)
                .forEach(itemsClasses::add);
    }

    private void group(TagKey<Item> tag, Set<Item> remaining) {
        var itemClass = MultiItemClass.ofTag(tag);

        if (itemClass == null) return;

        itemsClasses.add(itemClass);

        for (Item item : itemClass.items()) {
            remaining.remove(item);
        }
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

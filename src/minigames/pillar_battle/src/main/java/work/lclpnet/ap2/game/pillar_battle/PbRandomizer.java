package work.lclpnet.ap2.game.pillar_battle;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.pillar_battle.item.*;
import work.lclpnet.ap2.impl.tags.ApItemTags;
import work.lclpnet.gaco.ds.IndexedSet;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class PbRandomizer {

    private final Random random;
    private final Participants participants;
    private final RegistryAccess registryManager;
    private final IndexedSet<ItemClass> itemsClasses = new IndexedSet<>();

    public PbRandomizer(Random random, Participants participants, RegistryAccess registryManager) {
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
        group(ItemTags.BUNDLES);
        group(ApItemTags.TRIM_TEMPLATES);
        group(ApItemTags.FLOWERS);
        group(ApItemTags.DYES);
        group(ApItemTags.BANNER_PATTERNS);
        group(ItemTags.HARNESSES);
        group(ItemTags.SHULKER_BOXES);

        // potion items
        itemsClasses.add(new PotionItemClass(Items.POTION));
        itemsClasses.add(new PotionItemClass(Items.SPLASH_POTION));
        itemsClasses.add(new PotionItemClass(Items.LINGERING_POTION));
        itemsClasses.add(new PotionItemClass(Items.TIPPED_ARROW));

        // special classes
        itemsClasses.add(new EnchantedBookItemClass(registryManager));
        itemsClasses.add(new GoatHornItemClass(registryManager));
        itemsClasses.add(new SuspiciousStewItemClass());

        // add remaining items as singletons
        Set<Item> exclude = itemsClasses.stream()
                .flatMap(ItemClass::stream)
                .collect(Collectors.toSet());

        BuiltInRegistries.ITEM.stream()
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
        for (ServerPlayer player : participants) {
            giveRandomItem(player);
        }
    }

    private void giveRandomItem(ServerPlayer player) {
        ItemStack stack = getRandomStack();

        player.addItem(stack);
    }

    private ItemStack getRandomStack() {
        var itemClass = itemsClasses.get(random.nextInt(itemsClasses.size()));

        return itemClass.getRandomStack(random);
    }
}

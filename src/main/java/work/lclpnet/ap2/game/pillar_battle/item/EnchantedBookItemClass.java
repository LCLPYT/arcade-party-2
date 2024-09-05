package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import work.lclpnet.ap2.impl.util.ItemHelper;

import java.util.Random;
import java.util.stream.Stream;

public class EnchantedBookItemClass implements ItemClass {

    private final DynamicRegistryManager registryManager;

    public EnchantedBookItemClass(DynamicRegistryManager registryManager) {
        this.registryManager = registryManager;
    }

    @Override
    public ItemStack getRandomStack(Random random) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);

        var registry = registryManager.get(RegistryKeys.ENCHANTMENT);
        var entry = ItemHelper.getRandomEntry(registry, random);

        if (entry == null) return stack;

        Enchantment enchantment = entry.value();

        int minLevel = enchantment.getMinLevel();
        int level = minLevel + random.nextInt(enchantment.getMaxLevel() - minLevel + 1);

        var builder = new ItemEnchantmentsComponent.Builder(EnchantmentHelper.getEnchantments(stack));
        builder.set(entry, level);

        stack.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());

        return stack;
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(Items.ENCHANTED_BOOK);
    }
}

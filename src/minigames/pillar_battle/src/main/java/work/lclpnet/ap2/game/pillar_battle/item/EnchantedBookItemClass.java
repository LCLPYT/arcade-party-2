package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import work.lclpnet.ap2.impl.util.ItemHelper;

import java.util.Random;
import java.util.stream.Stream;

public class EnchantedBookItemClass implements ItemClass {

    private final RegistryAccess registryManager;

    public EnchantedBookItemClass(RegistryAccess registryManager) {
        this.registryManager = registryManager;
    }

    @Override
    public ItemStack getRandomStack(Random random) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);

        var registry = registryManager.lookupOrThrow(Registries.ENCHANTMENT);
        var entry = ItemHelper.getRandomEntry(registry, random);

        if (entry == null) return stack;

        Enchantment enchantment = entry.value();

        int minLevel = enchantment.getMinLevel();
        int level = minLevel + random.nextInt(enchantment.getMaxLevel() - minLevel + 1);

        var builder = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
        builder.set(entry, level);

        stack.set(DataComponents.STORED_ENCHANTMENTS, builder.toImmutable());

        return stack;
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(Items.ENCHANTED_BOOK);
    }
}

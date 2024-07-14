package work.lclpnet.ap2.impl.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Random;

public class ItemStackHelper {

    private ItemStackHelper() {}

    public static RegistryEntry<ArmorTrimPattern> getRandomTrimPattern(DynamicRegistryManager registryManager, Random random) {
        var registry = registryManager.get(RegistryKeys.TRIM_PATTERN);
        var entries = registry.getIndexedEntries();

        if (entries.size() <= 0) throw new IllegalStateException("There are no trim patterns registered");

        int idx = random.nextInt(entries.size());

        return entries.getOrThrow(idx);
    }

    public static RegistryEntry<ArmorTrimMaterial> getRandomTrimMaterial(DynamicRegistryManager registryManager, Random random) {
        var registry = registryManager.get(RegistryKeys.TRIM_MATERIAL);
        var entries = registry.getIndexedEntries();

        if (entries.size() <= 0) throw new IllegalStateException("There are no trim patterns registered");

        int idx = random.nextInt(entries.size());

        return entries.getOrThrow(idx);
    }

    public static RegistryEntry<ArmorTrimMaterial> getTrimMaterial(DynamicRegistryManager registryManager, RegistryKey<ArmorTrimMaterial> key) {
        var registry = registryManager.get(RegistryKeys.TRIM_MATERIAL);
        return registry.getEntry(key).orElseThrow();
    }

    public static RegistryEntry<Enchantment> getEnchantment(RegistryKey<Enchantment> enchantment, DynamicRegistryManager registryManager) {
        var enchantments = registryManager.get(RegistryKeys.ENCHANTMENT);
        return enchantments.getEntry(enchantment).orElseThrow();
    }
}

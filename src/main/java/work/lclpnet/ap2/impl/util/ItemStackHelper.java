package work.lclpnet.ap2.impl.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.Random;

public class ItemStackHelper {

    private ItemStackHelper() {}

    public static RegistryEntry<ArmorTrimPattern> getRandomTrimPattern(DynamicRegistryManager registryManager, Random random) {
        var registry = registryManager.getOrThrow(RegistryKeys.TRIM_PATTERN);
        var entries = registry.getIndexedEntries();

        if (entries.size() <= 0) throw new IllegalStateException("There are no trim patterns registered");

        int idx = random.nextInt(entries.size());

        return entries.getOrThrow(idx);
    }

    public static RegistryEntry<ArmorTrimMaterial> getRandomTrimMaterial(DynamicRegistryManager registryManager, Random random) {
        var registry = registryManager.getOrThrow(RegistryKeys.TRIM_MATERIAL);
        var entries = registry.getIndexedEntries();

        if (entries.size() <= 0) throw new IllegalStateException("There are no trim patterns registered");

        int idx = random.nextInt(entries.size());

        return entries.getOrThrow(idx);
    }

    public static RegistryEntry<ArmorTrimMaterial> getTrimMaterial(DynamicRegistryManager registryManager, RegistryKey<ArmorTrimMaterial> key) {
        var registry = registryManager.getOrThrow(RegistryKeys.TRIM_MATERIAL);
        return registry.getOptional(key).orElseThrow();
    }

    public static RegistryEntry<Enchantment> getEnchantment(RegistryKey<Enchantment> enchantment, DynamicRegistryManager registryManager) {
        var enchantments = registryManager.getOrThrow(RegistryKeys.ENCHANTMENT);
        return enchantments.getOptional(enchantment).orElseThrow();
    }
}

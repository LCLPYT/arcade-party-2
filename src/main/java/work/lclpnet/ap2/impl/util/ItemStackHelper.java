package work.lclpnet.ap2.impl.util;

import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potion;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.NoSuchElementException;
import java.util.Random;

public class ItemStackHelper {

    private ItemStackHelper() {}

    public static void setArmorTrim(ItemStack stack, RegistryKey<ArmorTrimPattern> pattern, RegistryKey<ArmorTrimMaterial> material) {
        NbtCompound nbt = stack.getOrCreateSubNbt("Trim");
        nbt.putString("pattern", pattern.getValue().toString());
        nbt.putString("material", material.getValue().toString());
    }

    public static RegistryKey<ArmorTrimPattern> getRandomTrimPattern(DynamicRegistryManager registryManager, Random random) {
        var registry = registryManager.get(RegistryKeys.TRIM_PATTERN);
        var keys = registry.getKeys();

        if (keys.isEmpty()) throw new IllegalStateException("There are no trim patterns registered");

        int idx = random.nextInt(keys.size());

        return keys.stream().skip(idx).findFirst().orElseThrow(() -> new NoSuchElementException("No trim pattern found"));
    }

    public static void setPotion(ItemStack stack, RegistryKey<Potion> potion) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putString("Potion", potion.getValue().toString());
    }
}

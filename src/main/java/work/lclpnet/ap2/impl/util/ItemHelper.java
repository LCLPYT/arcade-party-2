package work.lclpnet.ap2.impl.util;

import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public class ItemHelper {

    private ItemHelper() {}

    @Nullable
    public static Item getHelmet(RegistryEntry<ArmorMaterial> material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_HELMET;
        if (material == ArmorMaterials.CHAIN) return Items.CHAINMAIL_HELMET;
        if (material == ArmorMaterials.IRON) return Items.IRON_HELMET;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_HELMET;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_HELMET;
        if (material == ArmorMaterials.TURTLE) return Items.TURTLE_HELMET;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_HELMET;

        return null;
    }

    @Nullable
    public static Item getChestPlate(RegistryEntry<ArmorMaterial> material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_CHESTPLATE;
        if (material == ArmorMaterials.CHAIN) return Items.CHAINMAIL_CHESTPLATE;
        if (material == ArmorMaterials.IRON) return Items.IRON_CHESTPLATE;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_CHESTPLATE;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_CHESTPLATE;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_CHESTPLATE;

        return null;
    }

    @Nullable
    public static Item getLeggings(RegistryEntry<ArmorMaterial> material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_LEGGINGS;
        if (material == ArmorMaterials.CHAIN) return Items.CHAINMAIL_LEGGINGS;
        if (material == ArmorMaterials.IRON) return Items.IRON_LEGGINGS;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_LEGGINGS;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_LEGGINGS;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_LEGGINGS;

        return null;
    }

    @Nullable
    public static Item getBoots(RegistryEntry<ArmorMaterial> material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_BOOTS;
        if (material == ArmorMaterials.CHAIN) return Items.CHAINMAIL_BOOTS;
        if (material == ArmorMaterials.IRON) return Items.IRON_BOOTS;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_BOOTS;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_BOOTS;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_BOOTS;

        return null;
    }

    public static Optional<JukeboxSong> getJukeboxSong(Item musicDiscItem, RegistryWrapper.WrapperLookup registryLookup) {
        var component = musicDiscItem.getComponents().get(DataComponentTypes.JUKEBOX_PLAYABLE);

        if (component == null) {
            return Optional.empty();
        }

        return component.song()
                .getEntry(registryLookup)
                .map(RegistryEntry::value);
    }

    public static @NotNull RegistryEntry<Potion> getRandomPotion(Random random) {
        var potion = getRandomEntry(Registries.POTION, random);

        if (potion == null) {
            potion = Objects.requireNonNull(Potions.WATER);
        }

        return potion;
    }

    public static <T> @Nullable RegistryEntry<T> getRandomEntry(Registry<T> registry, Random random) {
        var entries = registry.getIndexedEntries();

        if (entries.size() <= 0) {
            return null;
        }

        return entries.get(random.nextInt(entries.size()));
    }
}

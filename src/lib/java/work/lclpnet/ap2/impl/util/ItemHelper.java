package work.lclpnet.ap2.impl.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.ArmorMaterials;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import static net.minecraft.component.DataComponentTypes.DYED_COLOR;
import static net.minecraft.component.DataComponentTypes.TOOLTIP_DISPLAY;

public class ItemHelper {

    private ItemHelper() {}

    @Nullable
    public static Item getHelmet(ArmorMaterial material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_HELMET;
        if (material == ArmorMaterials.CHAIN) return Items.CHAINMAIL_HELMET;
        if (material == ArmorMaterials.IRON) return Items.IRON_HELMET;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_HELMET;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_HELMET;
        if (material == ArmorMaterials.TURTLE_SCUTE) return Items.TURTLE_HELMET;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_HELMET;

        return null;
    }

    @Nullable
    public static Item getChestPlate(ArmorMaterial material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_CHESTPLATE;
        if (material == ArmorMaterials.CHAIN) return Items.CHAINMAIL_CHESTPLATE;
        if (material == ArmorMaterials.IRON) return Items.IRON_CHESTPLATE;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_CHESTPLATE;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_CHESTPLATE;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_CHESTPLATE;

        return null;
    }

    @Nullable
    public static Item getLeggings(ArmorMaterial material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_LEGGINGS;
        if (material == ArmorMaterials.CHAIN) return Items.CHAINMAIL_LEGGINGS;
        if (material == ArmorMaterials.IRON) return Items.IRON_LEGGINGS;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_LEGGINGS;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_LEGGINGS;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_LEGGINGS;

        return null;
    }

    @Nullable
    public static Item getBoots(ArmorMaterial material) {
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
                .resolveEntry(registryLookup)
                .map(RegistryEntry::value);
    }

    public static @NotNull RegistryEntry<Potion> getRandomPotion(Random random) {
        var potion = getRandomEntry(Registries.POTION, random);

        if (potion == null) {
            potion = Objects.requireNonNull(Potions.WATER);
        }

        return potion;
    }

    public static @Nullable RegistryEntry<StatusEffect> getRandomStatusEffect(Random random) {
        return getRandomEntry(Registries.STATUS_EFFECT, random);
    }

    public static <T> @Nullable RegistryEntry<T> getRandomEntry(Registry<T> registry, Random random) {
        var entries = registry.getIndexedEntries();

        if (entries.size() <= 0) {
            return null;
        }

        return entries.get(random.nextInt(entries.size()));
    }

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
        return registry.getOrThrow(key);
    }

    public static RegistryEntry<Enchantment> getEnchantment(RegistryKey<Enchantment> enchantment, DynamicRegistryManager registryManager) {
        var enchantments = registryManager.getOrThrow(RegistryKeys.ENCHANTMENT);
        return enchantments.getOrThrow(enchantment);
    }

    public static ItemStack unbreakable(ItemStack stack) {
        stack.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);

        var display = stack.getOrDefault(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT);

        stack.set(DataComponentTypes.TOOLTIP_DISPLAY, display.with(DataComponentTypes.UNBREAKABLE, true));

        return stack;
    }

    public static Optional<ItemStack> fromNbt(RegistryWrapper.WrapperLookup lookup, NbtCompound nbt) {
        return ItemStack.CODEC.decode(lookup.getOps(NbtOps.INSTANCE), nbt)
                .resultOrPartial()
                .map(Pair::getFirst);
    }

    public static @NotNull ItemStack getLeatherArmor(Item leatherChestplate, int color) {
        ItemStack chestPlate = new ItemStack(leatherChestplate);

        chestPlate.set(DYED_COLOR, new DyedColorComponent(color));
        chestPlate.set(TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DYED_COLOR, true));

        return chestPlate;
    }

    public static <T> void setCustomData(ItemStack stack, MapEncoder<T> encoder, T value) {
        stack.set(DataComponentTypes.CUSTOM_DATA, stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .with(NbtOps.INSTANCE, encoder, value)
                .getOrThrow());
    }

    public static <T> Optional<T> getCustomData(ItemStack stack, MapDecoder<T> decoder) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .get(decoder)
                .resultOrPartial();
    }
}

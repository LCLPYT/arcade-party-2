package work.lclpnet.ap2.impl.util;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import static net.minecraft.core.component.DataComponents.DYED_COLOR;
import static net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY;

public class ItemHelper {

    private ItemHelper() {}

    @Nullable
    public static Item getHelmet(ArmorMaterial material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_HELMET;
        if (material == ArmorMaterials.CHAINMAIL) return Items.CHAINMAIL_HELMET;
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
        if (material == ArmorMaterials.CHAINMAIL) return Items.CHAINMAIL_CHESTPLATE;
        if (material == ArmorMaterials.IRON) return Items.IRON_CHESTPLATE;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_CHESTPLATE;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_CHESTPLATE;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_CHESTPLATE;

        return null;
    }

    @Nullable
    public static Item getLeggings(ArmorMaterial material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_LEGGINGS;
        if (material == ArmorMaterials.CHAINMAIL) return Items.CHAINMAIL_LEGGINGS;
        if (material == ArmorMaterials.IRON) return Items.IRON_LEGGINGS;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_LEGGINGS;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_LEGGINGS;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_LEGGINGS;

        return null;
    }

    @Nullable
    public static Item getBoots(ArmorMaterial material) {
        if (material == ArmorMaterials.LEATHER) return Items.LEATHER_BOOTS;
        if (material == ArmorMaterials.CHAINMAIL) return Items.CHAINMAIL_BOOTS;
        if (material == ArmorMaterials.IRON) return Items.IRON_BOOTS;
        if (material == ArmorMaterials.GOLD) return Items.GOLDEN_BOOTS;
        if (material == ArmorMaterials.DIAMOND) return Items.DIAMOND_BOOTS;
        if (material == ArmorMaterials.NETHERITE) return Items.NETHERITE_BOOTS;

        return null;
    }

    public static Optional<JukeboxSong> getJukeboxSong(Item musicDiscItem) {
        var component = musicDiscItem.components().get(DataComponents.JUKEBOX_PLAYABLE);

        if (component == null) {
            return Optional.empty();
        }

        return Optional.of(component.song().value());
    }

    public static @NotNull Holder<Potion> getRandomPotion(Random random) {
        var potion = getRandomEntry(BuiltInRegistries.POTION, random);

        if (potion == null) {
            potion = Objects.requireNonNull(Potions.WATER);
        }

        return potion;
    }

    public static @Nullable Holder<MobEffect> getRandomStatusEffect(Random random) {
        return getRandomEntry(BuiltInRegistries.MOB_EFFECT, random);
    }

    public static <T> @Nullable Holder<T> getRandomEntry(Registry<T> registry, Random random) {
        var entries = registry.asHolderIdMap();

        if (entries.size() <= 0) {
            return null;
        }

        return entries.byId(random.nextInt(entries.size()));
    }

    public static Holder<TrimPattern> getRandomTrimPattern(RegistryAccess registryManager, Random random) {
        var registry = registryManager.lookupOrThrow(Registries.TRIM_PATTERN);
        var entries = registry.asHolderIdMap();

        if (entries.size() <= 0) throw new IllegalStateException("There are no trim patterns registered");

        int idx = random.nextInt(entries.size());

        return entries.byIdOrThrow(idx);
    }

    public static Holder<TrimMaterial> getRandomTrimMaterial(RegistryAccess registryManager, Random random) {
        var registry = registryManager.lookupOrThrow(Registries.TRIM_MATERIAL);
        var entries = registry.asHolderIdMap();

        if (entries.size() <= 0) throw new IllegalStateException("There are no trim patterns registered");

        int idx = random.nextInt(entries.size());

        return entries.byIdOrThrow(idx);
    }

    public static Holder<TrimMaterial> getTrimMaterial(RegistryAccess registryManager, ResourceKey<TrimMaterial> key) {
        var registry = registryManager.lookupOrThrow(Registries.TRIM_MATERIAL);
        return registry.getOrThrow(key);
    }

    public static Holder<Enchantment> getEnchantment(ResourceKey<Enchantment> enchantment, RegistryAccess registryManager) {
        var enchantments = registryManager.lookupOrThrow(Registries.ENCHANTMENT);
        return enchantments.getOrThrow(enchantment);
    }

    public static ItemStack unbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);

        var display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        stack.set(DataComponents.TOOLTIP_DISPLAY, display.withHidden(DataComponents.UNBREAKABLE, true));

        return stack;
    }

    public static Optional<ItemStack> fromNbt(HolderLookup.Provider lookup, CompoundTag nbt) {
        return ItemStack.CODEC.decode(lookup.createSerializationContext(NbtOps.INSTANCE), nbt)
                .resultOrPartial()
                .map(Pair::getFirst);
    }

    public static @NotNull ItemStack getLeatherArmor(Item leatherChestplate, int color) {
        ItemStack chestPlate = new ItemStack(leatherChestplate);

        chestPlate.set(DYED_COLOR, new DyedItemColor(color));
        chestPlate.set(TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DYED_COLOR, true));

        return chestPlate;
    }

    public static ItemStack getStackWithData(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        ItemStack stack = state.getCloneItemStack(world, pos, true);

        if (!stack.isEmpty()) {
            copyBlockDataToStack(state, world, pos, stack);
        }

        return stack;
    }

    public static void copyBlockDataToStack(BlockState state, ServerLevel world, BlockPos pos, ItemStack stack) {
        BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;

        if (blockEntity == null) return;

        try (ProblemReporter.ScopedCollector logging = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), ApConstants.logger)) {
            TagValueOutput nbtWriteView = TagValueOutput.createWithContext(logging, world.registryAccess());
            blockEntity.saveCustomOnly(nbtWriteView);
            //noinspection deprecation
            blockEntity.removeComponentsFromTag(nbtWriteView);
            BlockItem.setBlockEntityData(stack, blockEntity.getType(), nbtWriteView);
            stack.applyComponents(blockEntity.collectComponents());
        }
    }
}

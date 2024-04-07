package work.lclpnet.ap2.impl.util;

import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;

public class ItemHelper {

    private ItemHelper() {}

    public static Item getHelmet(ArmorMaterials material) {
        return switch (material) {
            case LEATHER -> Items.LEATHER_HELMET;
            case CHAIN -> Items.CHAINMAIL_HELMET;
            case IRON -> Items.IRON_HELMET;
            case GOLD -> Items.GOLDEN_HELMET;
            case DIAMOND -> Items.DIAMOND_HELMET;
            case TURTLE -> Items.TURTLE_HELMET;
            case NETHERITE -> Items.NETHERITE_HELMET;
        };
    }

    @Nullable
    public static Item getChestPlate(ArmorMaterials material) {
        return switch (material) {
            case LEATHER -> Items.LEATHER_CHESTPLATE;
            case CHAIN -> Items.CHAINMAIL_CHESTPLATE;
            case IRON -> Items.IRON_CHESTPLATE;
            case GOLD -> Items.GOLDEN_CHESTPLATE;
            case DIAMOND -> Items.DIAMOND_CHESTPLATE;
            case TURTLE -> null;
            case NETHERITE -> Items.NETHERITE_CHESTPLATE;
        };
    }

    @Nullable
    public static Item getLeggings(ArmorMaterials material) {
        return switch (material) {
            case LEATHER -> Items.LEATHER_LEGGINGS;
            case CHAIN -> Items.CHAINMAIL_LEGGINGS;
            case IRON -> Items.IRON_LEGGINGS;
            case GOLD -> Items.GOLDEN_LEGGINGS;
            case DIAMOND -> Items.DIAMOND_LEGGINGS;
            case TURTLE -> null;
            case NETHERITE -> Items.NETHERITE_LEGGINGS;
        };
    }

    @Nullable
    public static Item getBoots(ArmorMaterials material) {
        return switch (material) {
            case LEATHER -> Items.LEATHER_BOOTS;
            case CHAIN -> Items.CHAINMAIL_BOOTS;
            case IRON -> Items.IRON_BOOTS;
            case GOLD -> Items.GOLDEN_BOOTS;
            case DIAMOND -> Items.DIAMOND_BOOTS;
            case TURTLE -> null;
            case NETHERITE -> Items.NETHERITE_BOOTS;
        };
    }
}

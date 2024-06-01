package work.lclpnet.ap2.impl.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.DyeColor;

public class BlockHelper {

    private BlockHelper() {}

    public static Block getWool(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_WOOL;
            case ORANGE -> Blocks.ORANGE_WOOL;
            case MAGENTA -> Blocks.MAGENTA_WOOL;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_WOOL;
            case YELLOW -> Blocks.YELLOW_WOOL;
            case LIME -> Blocks.LIME_WOOL;
            case PINK -> Blocks.PINK_WOOL;
            case GRAY -> Blocks.GRAY_WOOL;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_WOOL;
            case CYAN -> Blocks.CYAN_WOOL;
            case PURPLE -> Blocks.PURPLE_WOOL;
            case BLUE -> Blocks.BLUE_WOOL;
            case BROWN -> Blocks.BROWN_WOOL;
            case GREEN -> Blocks.GREEN_WOOL;
            case RED -> Blocks.RED_WOOL;
            case BLACK -> Blocks.BLACK_WOOL;
        };
    }

    public static Block getConcrete(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_CONCRETE;
            case ORANGE -> Blocks.ORANGE_CONCRETE;
            case MAGENTA -> Blocks.MAGENTA_CONCRETE;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CONCRETE;
            case YELLOW -> Blocks.YELLOW_CONCRETE;
            case LIME -> Blocks.LIME_CONCRETE;
            case PINK -> Blocks.PINK_CONCRETE;
            case GRAY -> Blocks.GRAY_CONCRETE;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CONCRETE;
            case CYAN -> Blocks.CYAN_CONCRETE;
            case PURPLE -> Blocks.PURPLE_CONCRETE;
            case BLUE -> Blocks.BLUE_CONCRETE;
            case BROWN -> Blocks.BROWN_CONCRETE;
            case GREEN -> Blocks.GREEN_CONCRETE;
            case RED -> Blocks.RED_CONCRETE;
            case BLACK -> Blocks.BLACK_CONCRETE;
        };
    }

    public static Block getConcretePowder(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_CONCRETE_POWDER;
            case ORANGE -> Blocks.ORANGE_CONCRETE_POWDER;
            case MAGENTA -> Blocks.MAGENTA_CONCRETE_POWDER;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CONCRETE_POWDER;
            case YELLOW -> Blocks.YELLOW_CONCRETE_POWDER;
            case LIME -> Blocks.LIME_CONCRETE_POWDER;
            case PINK -> Blocks.PINK_CONCRETE_POWDER;
            case GRAY -> Blocks.GRAY_CONCRETE_POWDER;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CONCRETE_POWDER;
            case CYAN -> Blocks.CYAN_CONCRETE_POWDER;
            case PURPLE -> Blocks.PURPLE_CONCRETE_POWDER;
            case BLUE -> Blocks.BLUE_CONCRETE_POWDER;
            case BROWN -> Blocks.BROWN_CONCRETE_POWDER;
            case GREEN -> Blocks.GREEN_CONCRETE_POWDER;
            case RED -> Blocks.RED_CONCRETE_POWDER;
            case BLACK -> Blocks.BLACK_CONCRETE_POWDER;
        };
    }

    public static Block getStainedGlass(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_STAINED_GLASS;
            case ORANGE -> Blocks.ORANGE_STAINED_GLASS;
            case MAGENTA -> Blocks.MAGENTA_STAINED_GLASS;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            case YELLOW -> Blocks.YELLOW_STAINED_GLASS;
            case LIME -> Blocks.LIME_STAINED_GLASS;
            case PINK -> Blocks.PINK_STAINED_GLASS;
            case GRAY -> Blocks.GRAY_STAINED_GLASS;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_STAINED_GLASS;
            case CYAN -> Blocks.CYAN_STAINED_GLASS;
            case PURPLE -> Blocks.PURPLE_STAINED_GLASS;
            case BLUE -> Blocks.BLUE_STAINED_GLASS;
            case BROWN -> Blocks.BROWN_STAINED_GLASS;
            case GREEN -> Blocks.GREEN_STAINED_GLASS;
            case RED -> Blocks.RED_STAINED_GLASS;
            case BLACK -> Blocks.BLACK_STAINED_GLASS;
        };
    }
}

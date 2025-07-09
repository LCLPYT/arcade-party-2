package work.lclpnet.ap2.api.game.team;

import net.minecraft.util.Formatting;

public interface TeamKey {

    String id();

    Formatting colorFormat();

    default int color() {
        Integer value = colorFormat().getColorValue();

        return value == null ? 0x000000 : value;
    }

    default String getTranslationKey() {
        return "ap2.team." + id();
    }
}

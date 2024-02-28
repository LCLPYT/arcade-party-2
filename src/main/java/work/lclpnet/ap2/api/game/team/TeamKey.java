package work.lclpnet.ap2.api.game.team;

import net.minecraft.util.Formatting;

public interface TeamKey {

    String id();

    Formatting colorFormat();

    default String getTranslationKey() {
        return "ap2.team." + id();
    }
}

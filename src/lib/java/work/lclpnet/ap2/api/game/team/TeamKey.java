package work.lclpnet.ap2.api.game.team;

import net.minecraft.util.Formatting;

public sealed interface TeamKey permits DyeTeamKey {

    String id();

    int color();

    Formatting formatting();

    default String getTranslationKey() {
        return "ap2.team." + id();
    }
}

package work.lclpnet.ap2.api.game.team;

import net.minecraft.ChatFormatting;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

public sealed interface TeamKey permits DyeTeamKey {

    String id();

    int color();

    ChatFormatting formatting();

    default String getTranslationKey() {
        return "ap2.team." + id();
    }

    default TranslatedText getDisplayName(Translations translations) {
        return translations.translateText(getTranslationKey())
                .styled(style -> style.withColor(color()));
    }
}

package work.lclpnet.ap2.impl.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreboardObjective;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.ap2.impl.util.scoreboard.TranslatedScoreboardObjective;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.ChatFormatting.*;

public class ScoreboardUtil {

    private ScoreboardUtil() {}

    public static @NotNull TranslatedScoreboardObjective setupSidebar(CustomScoreboardManager manager, String titleTranslationKey) {
        var objective = manager.translateObjective("score", titleTranslationKey).formatted(AQUA, BOLD);

        objective.setSlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(holder -> Component.literal(holder).withStyle(GREEN));
        objective.setNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT);

        // separators at top and bottom
        var separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR).withStyle(DARK_GREEN, STRIKETHROUGH, BOLD);
        objective.createText(separator, ScoreboardLayout.TOP);
        objective.createText(separator, ScoreboardLayout.BOTTOM);

        return objective;
    }

    public static @NotNull DynamicScoreboardObjective setupDynamicSidebar(CustomScoreboardManager manager, String titleTranslationKey) {
        TranslatedText title = manager.getTranslations().translateText(titleTranslationKey).formatted(AQUA, BOLD);
        var objective = manager.createDynamicObjective("score", title::translateFor);

        objective.setSlot(DisplaySlot.SIDEBAR);
        objective.setDefaultDisplay((_, holder) -> Component.literal(holder).withStyle(GREEN));
        objective.setDefaultNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT);

        // separators at top and bottom
        var separator = Component.literal(ApConstants.SCOREBOARD_SEPARATOR).withStyle(DARK_GREEN, STRIKETHROUGH, BOLD);
        objective.createText(separator, ScoreboardLayout.TOP);
        objective.createText(separator, ScoreboardLayout.BOTTOM);

        return objective;
    }
}

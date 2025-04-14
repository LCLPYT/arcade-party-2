package work.lclpnet.ap2.impl.util;

import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.scoreboard.DynamicScoreboardObjective;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.ap2.impl.util.scoreboard.TranslatedScoreboardObjective;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.util.Formatting.*;

public class ScoreboardUtil {

    private ScoreboardUtil() {}

    public static @NotNull TranslatedScoreboardObjective setupSidebar(CustomScoreboardManager manager, String titleTranslationKey) {
        var objective = manager.translateObjective("score", titleTranslationKey).formatted(AQUA, BOLD);

        objective.setSlot(ScoreboardDisplaySlot.SIDEBAR);
        objective.setDisplayName(holder -> Text.literal(holder).formatted(GREEN));
        objective.setNumberFormat(StyledNumberFormat.YELLOW);

        // separators at top and bottom
        var separator = Text.literal(ApConstants.SCOREBOARD_SEPARATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);
        objective.createText(separator, ScoreboardLayout.TOP);
        objective.createText(separator, ScoreboardLayout.BOTTOM);

        return objective;
    }

    public static @NotNull DynamicScoreboardObjective setupDynamicSidebar(CustomScoreboardManager manager, String titleTranslationKey) {
        TranslatedText title = manager.getTranslations().translateText(titleTranslationKey).formatted(AQUA, BOLD);
        var objective = manager.createDynamicObjective("score", title::translateFor);

        objective.setSlot(ScoreboardDisplaySlot.SIDEBAR);
        objective.setDefaultDisplay((player, holder) -> Text.literal(holder).formatted(GREEN));
        objective.setDefaultNumberFormat(StyledNumberFormat.YELLOW);

        // separators at top and bottom
        var separator = Text.literal(ApConstants.SCOREBOARD_SEPARATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);
        objective.createText(separator, ScoreboardLayout.TOP);
        objective.createText(separator, ScoreboardLayout.BOTTOM);

        return objective;
    }
}

package work.lclpnet.ap2.impl.util;

import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.scoreboard.ScoreboardLayout;
import work.lclpnet.ap2.impl.util.scoreboard.TranslatedScoreboardObjective;

import static net.minecraft.util.Formatting.*;

public class ScoreboardUtil {

    private ScoreboardUtil() {}

    public static @NotNull TranslatedScoreboardObjective setupSidebar(CustomScoreboardManager manager, String titleTranslationKey) {
        var objective = manager.translateObjective("score", titleTranslationKey).formatted(AQUA, Formatting.BOLD);

        objective.setSlot(ScoreboardDisplaySlot.SIDEBAR);
        objective.setDisplayName(holder -> Text.literal(holder).formatted(GREEN));
        objective.setNumberFormat(StyledNumberFormat.YELLOW);

        // separators at top and bottom
        var separator = Text.literal(ApConstants.SCOREBOARD_SEPARATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);
        objective.createText(separator, ScoreboardLayout.TOP);
        objective.createText(separator, ScoreboardLayout.BOTTOM);

        return objective;
    }
}

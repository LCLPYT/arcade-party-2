package work.lclpnet.ap2.impl.util.scoreboard;

import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.text.TextTranslatable;

public class ScoreHandle {

    private final String holder;
    private final TranslatedScoreboardObjective objective;

    public ScoreHandle(String holder, TranslatedScoreboardObjective objective) {
        this.holder = holder;
        this.objective = objective;
    }

    public void setScore(int score) {
        objective.setScore(holder, score);
    }

    public int getScore() {
        return objective.getScore(holder);
    }

    public void setDisplay(@Nullable Text text) {
        objective.setDisplayName(holder, text);
    }

    public void setDisplay(@Nullable TextTranslatable text) {
        objective.setDisplayName(holder, text);
    }

    public void setNumberFormat(NumberFormat numberFormat) {
        objective.setNumberFormat(holder, numberFormat);
    }
}

package work.lclpnet.ap2.impl.util.scoreboard;

import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.scoreboard.CustomScoreboardObjective;

public class ScoreHandle {

    @Getter
    private final String holder;
    private final CustomScoreboardObjective objective;

    public ScoreHandle(String holder, CustomScoreboardObjective objective) {
        this.holder = holder;
        this.objective = objective;
    }

    public void setScore(int score) {
        objective.setScore(holder, score);
    }

    public void setDisplay(@Nullable Component text) {
        objective.setDisplayName(holder, text);
    }

    public void setNumberFormat(NumberFormat numberFormat) {
        objective.setNumberFormat(holder, numberFormat);
    }
}

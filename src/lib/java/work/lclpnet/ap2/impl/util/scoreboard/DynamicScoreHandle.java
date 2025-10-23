package work.lclpnet.ap2.impl.util.scoreboard;

import lombok.Getter;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class DynamicScoreHandle {

    @Getter
    private final String holder;
    private final DynamicScoreboardObjective objective;

    public DynamicScoreHandle(String holder, DynamicScoreboardObjective objective) {
        this.holder = holder;
        this.objective = objective;
    }

    public void setScore(ServerPlayerEntity player, int score) {
        objective.setScore(player, holder, score);
    }

    public void setDisplay(ServerPlayerEntity player, @Nullable Text text) {
        objective.setDisplayName(player, holder, text);
    }

    public void setNumberFormat(ServerPlayerEntity player, NumberFormat numberFormat) {
        objective.setNumberFormat(player, holder, numberFormat);
    }
}

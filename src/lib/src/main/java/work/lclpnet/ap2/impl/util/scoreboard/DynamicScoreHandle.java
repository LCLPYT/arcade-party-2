package work.lclpnet.ap2.impl.util.scoreboard;

import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class DynamicScoreHandle {

    @Getter
    private final String holder;
    private final DynamicScoreboardObjective objective;

    public DynamicScoreHandle(String holder, DynamicScoreboardObjective objective) {
        this.holder = holder;
        this.objective = objective;
    }

    public void setScore(ServerPlayer player, int score) {
        objective.setScore(player, holder, score);
    }

    public void setDisplay(ServerPlayer player, @Nullable Component text) {
        objective.setDisplayName(player, holder, text);
    }

    public void setNumberFormat(ServerPlayer player, NumberFormat numberFormat) {
        objective.setNumberFormat(player, holder, numberFormat);
    }
}

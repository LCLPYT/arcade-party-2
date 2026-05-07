package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public interface CustomScoreboardObjective {

    void setScore(String scoreHolder, int score);

    void setDisplayName(String scoreHolder, @Nullable Component display);

    void setNumberFormat(String scoreHolder, NumberFormat numberFormat);

    void removeEntry(String scoreHolder);

    default void setScore(ServerPlayer player, int score) {
        setScore(player.getScoreboardName(), score);
    }
}

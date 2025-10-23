package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public interface CustomScoreboardObjective {

    void setScore(String scoreHolder, int score);

    void setDisplayName(String scoreHolder, @Nullable Text display);

    void setNumberFormat(String scoreHolder, NumberFormat numberFormat);

    void removeEntry(String scoreHolder);

    default void setScore(ServerPlayerEntity player, int score) {
        setScore(player.getNameForScoreboard(), score);
    }
}

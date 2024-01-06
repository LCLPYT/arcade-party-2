package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public interface CustomScoreboard {

    ScoreboardObjective createObjective(String name, ScoreboardCriterion criterion, Text displayName,
                                        ScoreboardCriterion.RenderType renderType);

    ScoreboardPlayerScore getPlayerScore(String playerName, ScoreboardObjective objective);

    default ScoreboardPlayerScore getPlayerScore(ServerPlayerEntity player, ScoreboardObjective objective) {
        return getPlayerScore(player.getEntityName(), objective);
    }
}

package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.server.network.ServerPlayerEntity;

public interface CustomScoreboardObjective {

    int getScore(String scoreHolder);

    void setScore(String scoreHolder, int score);

    default int getScore(ServerPlayerEntity player) {
        return getScore(player.getNameForScoreboard());
    }

    default void setScore(ServerPlayerEntity player, int score) {
        setScore(player.getNameForScoreboard(), score);
    }
}

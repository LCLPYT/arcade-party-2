package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.server.network.ServerPlayerEntity;

public interface CustomScoreboardObjective {

    int getScore(String playerName);

    void setScore(String playerName, int score);

    default int getScore(ServerPlayerEntity player) {
        return getScore(player.getEntityName());
    }

    default void setScore(ServerPlayerEntity player, int score) {
        setScore(player.getEntityName(), score);
    }
}

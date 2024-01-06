package work.lclpnet.ap2.api.util.scoreboard;

import net.minecraft.server.network.ServerPlayerEntity;

public interface CustomScoreboardObjective {

    void setScore(ServerPlayerEntity player, int score);
}

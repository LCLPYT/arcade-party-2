package work.lclpnet.ap2.base.util;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import lombok.Getter;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;

public class ScoreManager {

    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreDataContainer<>(PlayerRef::create);
    @Getter
    private final int targetScore;
    @Getter
    private int round = 0;

    public ScoreManager(int targetScore) {
        this.targetScore = targetScore;
    }

    public void addScore(PlayerRef player, int score) {
        if (score <= 0) return;

        data.addScore(player, score);
    }

    public int getScore(PlayerRef ref) {
        return data.getScore(ref);
    }

    public void incrementRound() {
        round++;
    }

    public Iterable<ObjectIntPair<PlayerRef>> iterateRankedScores() {
        return data::getRankedEntries;
    }

    public boolean hasScores() {
        return !data.isEmpty();
    }
}

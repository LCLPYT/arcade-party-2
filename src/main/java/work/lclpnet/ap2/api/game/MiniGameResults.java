package work.lclpnet.ap2.api.game;

import lombok.Getter;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.RankUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.max;

public class MiniGameResults {

    public static final MiniGameResults EMPTY = new MiniGameResults(Status.CANCELLED, Map.of());

    @Getter
    private final Status status;
    private final Map<PlayerRef, PlayerResult> entries;

    public MiniGameResults(Status status, Map<PlayerRef, PlayerResult> entries) {
        this.status = status;
        this.entries = entries;
    }

    public Set<PlayerResult> getEntries() {
        return Set.copyOf(entries.values());
    }

    public List<Set<PlayerResult>> getEntriesByRank() {
        return RankUtil.rank(entries.values()::stream, PlayerResult::getRank).toList();
    }

    @Getter
    public static class PlayerResult {
        private final PlayerRef ref;
        private final int rank;
        private int coinsAcquired = 0;

        public PlayerResult(PlayerRef ref, int rank) {
            this.ref = ref;
            this.rank = rank;
        }

        public void setCoinsAcquired(int coinsAcquired) {
            this.coinsAcquired = max(0, coinsAcquired);
        }
    }

    public enum Status {
        SUCCESS,
        CANCELLED
    }
}

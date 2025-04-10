package work.lclpnet.ap2.api.game;

import lombok.Getter;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.max;

public class MiniGameResults {

    public static final MiniGameResults EMPTY = new MiniGameResults(Map.of());

    private final Map<PlayerRef, PlayerResult> entries;

    public MiniGameResults(Map<PlayerRef, PlayerResult> entries) {
        this.entries = entries;
    }

    public Set<PlayerResult> getEntries() {
        return Set.copyOf(entries.values());
    }

    public List<Set<PlayerResult>> getEntriesByRank() {
        return entries.values().stream()
                .collect(Collectors.groupingBy(playerResult -> playerResult.rank))
                .entrySet().stream()
                .sorted(Comparator.<Entry<Integer, List<PlayerResult>>>comparingInt(Entry::getKey).reversed())
                .map(Entry::getValue)
                .map(Set::copyOf)
                .toList();
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
}

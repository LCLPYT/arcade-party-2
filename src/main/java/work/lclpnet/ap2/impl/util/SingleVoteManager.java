package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import work.lclpnet.ap2.api.util.VoteManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SingleVoteManager<T> implements VoteManager<T> {

    private final Map<UUID, T> voted = new HashMap<>();
    private final Object2IntMap<T> votes = new Object2IntOpenHashMap<>();
    private final boolean allowChange;

    public SingleVoteManager(boolean allowChange) {
        this.allowChange = allowChange;
    }

    public synchronized boolean vote(UUID voter, T value) {
        if (!allowChange && voted.containsKey(voter)) {
            return false;
        }

        T prev = voted.put(voter, value);

        if (prev == value) return false;

        votes.computeInt(prev, (t, count) -> count == null || count == 1 ? null : count - 1);
        votes.computeInt(value, (t, count) -> count == null ? 1 : count + 1);

        return true;
    }

    public synchronized Optional<T> getVote(UUID voter) {
        return Optional.ofNullable(voted.get(voter));
    }

    @Override
    public Object2IntMap<T> votes() {
        return Object2IntMaps.unmodifiable(votes);
    }
}

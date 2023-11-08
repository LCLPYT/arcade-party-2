package work.lclpnet.ap2.impl.game.data;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerRef;
import work.lclpnet.ap2.impl.game.data.entry.ScoreDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreTimeDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreView;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScoreTimeDataContainer implements DataContainer {

    private final Object2IntMap<PlayerRef> score = new Object2IntOpenHashMap<>();
    private final LinkedHashSet<UUID> lastModified = new LinkedHashSet<>();

    private boolean frozen = false;

    public void setScore(ServerPlayerEntity player, int score) {
        synchronized (this) {
            if (frozen) return;
            this.score.put(PlayerRef.create(player), score);
            touchInternal(player);
        }
    }

    public void addScore(ServerPlayerEntity player, int add) {
        synchronized (this) {
            if (frozen) return;
            PlayerRef key = PlayerRef.create(player);
            score.put(key, score.computeIfAbsent(key, playerRef -> 0) + add);
            touchInternal(player);
        }
    }

    public int getScore(ServerPlayerEntity player) {
        synchronized (this) {
            return score.computeIfAbsent(PlayerRef.create(player), playerRef -> 0);
        }
    }

    // requires external synchronization
    private void touchInternal(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        lastModified.remove(uuid);
        lastModified.add(uuid);
    }

    @Override
    public void delete(ServerPlayerEntity player) {
        synchronized (this) {
            if (frozen) return;
            score.removeInt(PlayerRef.create(player));
        }
    }

    @Override
    public DataEntry getEntry(ServerPlayerEntity player) {
        PlayerRef ref = PlayerRef.create(player);

        return getEntry(ref);
    }

    @NotNull
    private DataEntry getEntry(PlayerRef ref) {
        synchronized (this) {
            int score = this.score.getInt(ref);
            int ranking = getTimedRanking0(ref);

            if (ranking == 0) {
                return new ScoreDataEntry(ref, score);
            }

            return new ScoreTimeDataEntry(ref, score, ranking);
        }
    }

    @Override
    public Stream<? extends DataEntry> orderedEntries() {
        return score.object2IntEntrySet().stream()
                .map(e -> {
                    PlayerRef ref = e.getKey();
                    int ranking = getTimedRanking(ref);

                    if (ranking == 0) {
                        return new ScoreDataEntry(ref, e.getIntValue());
                    }

                    return new ScoreTimeDataEntry(ref, e.getIntValue(), ranking);
                })
                .sorted(Comparator.comparingInt(ScoreView::score).reversed().thenComparingInt(x -> {
                    if (x instanceof ScoreTimeDataEntry scoreTime) {
                        return scoreTime.ranking();
                    }

                    return 0;
                }));
    }

    /**
     * Get the ranking among players with the same score.
     * @param ref The player reference.
     * @return The ranking, or 0 if the score is unique.
     */
    private int getTimedRanking(PlayerRef ref) {
        synchronized (this) {
            return getTimedRanking0(ref);
        }
    }

    // requires external synchronization
    private int getTimedRanking0(PlayerRef ref) {
        int playerScore = score.getInt(ref);

        var sameScore = score.object2IntEntrySet().stream()
                .filter(e -> e.getIntValue() == playerScore)
                .map(Map.Entry::getKey)
                .map(PlayerRef::uuid)
                .collect(Collectors.toSet());

        if (sameScore.size() <= 1) {
            // there is only one player who has this score
            return 0;
        }

        int rank = 1;

        UUID playerUuid = ref.uuid();

        for (UUID uuid : lastModified) {
            if (!sameScore.contains(uuid)) continue;

            if (uuid.equals(playerUuid)) break;

            rank++;
        }

        return rank;
    }

    @Override
    public void freeze() {
        synchronized (this) {
            frozen = true;
        }
    }

    @Override
    public void ensureTracked(ServerPlayerEntity player) {
        addScore(player, 0);
    }
}

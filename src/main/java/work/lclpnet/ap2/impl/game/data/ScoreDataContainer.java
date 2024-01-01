package work.lclpnet.ap2.impl.game.data;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.event.IntScoreEvent;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerRef;
import work.lclpnet.ap2.impl.game.data.entry.ScoreDataEntry;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScoreDataContainer implements DataContainer, IntScoreEventSource {

    private final Map<PlayerRef, Integer> scoreMap = new HashMap<>();
    private final List<IntScoreEvent> listeners = new ArrayList<>();

    private boolean frozen = false;

    public void setScore(ServerPlayerEntity player, int score) {
        synchronized (this) {
            if (frozen) return;
            scoreMap.put(PlayerRef.create(player), score);
        }

        listeners.forEach(listener -> listener.accept(player, score));
    }

    public void addScore(ServerPlayerEntity player, int add) {
        synchronized (this) {
            if (frozen) return;
            PlayerRef key = PlayerRef.create(player);
            scoreMap.put(key, scoreMap.computeIfAbsent(key, playerRef -> 0) + add);
        }
    }

    public int getScore(ServerPlayerEntity player) {
        synchronized (this) {
            return scoreMap.computeIfAbsent(PlayerRef.create(player), playerRef -> 0);
        }
    }

    @Override
    public void delete(ServerPlayerEntity player) {
        synchronized (this) {
            if (frozen) return;
            scoreMap.remove(PlayerRef.create(player));
        }
    }

    @Override
    public DataEntry getEntry(ServerPlayerEntity player) {
        PlayerRef ref = PlayerRef.create(player);
        synchronized (this) {
            return new ScoreDataEntry(ref, scoreMap.get(ref));
        }
    }

    @Override
    public Stream<? extends DataEntry> orderedEntries() {
        return scoreMap.entrySet().stream()
                .map(e -> new ScoreDataEntry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(ScoreDataEntry::score).reversed());
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

    public OptionalInt getBestScore() {
        return scoreMap.values().stream().mapToInt(i -> i).max();
    }

    public Set<ServerPlayerEntity> getBestPlayers(MinecraftServer server) {
        OptionalInt best = getBestScore();

        if (best.isEmpty()) return Set.of();

        final int bestScore = best.getAsInt();

        return scoreMap.keySet().stream()
                .filter(ref -> scoreMap.get(ref) == bestScore)
                .map(ref -> ref.resolve(server))
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void register(IntScoreEvent listener) {
        listeners.add(Objects.requireNonNull(listener));
    }
}

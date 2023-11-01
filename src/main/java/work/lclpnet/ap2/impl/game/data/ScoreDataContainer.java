package work.lclpnet.ap2.impl.game.data;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerRef;
import work.lclpnet.ap2.impl.game.data.entry.ScoreDataEntry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.stream.Stream;

public class ScoreDataContainer implements DataContainer {

    private final HashMap<PlayerRef, Integer> scoreMap = new HashMap<>();

    private boolean frozen = false;

    public void setScore(ServerPlayerEntity player, int score) {
        synchronized (this) {
            if (frozen) return;
            scoreMap.put(PlayerRef.create(player), score);
        }
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
                .map(playerRefIntegerEntry -> new ScoreDataEntry(playerRefIntegerEntry.getKey(), playerRefIntegerEntry.getValue()))
                .sorted(Comparator.comparingInt(ScoreDataEntry::score));
    }

    @Override
    public void freeze() {
        synchronized (this) {
            frozen = true;
        }
    }
}

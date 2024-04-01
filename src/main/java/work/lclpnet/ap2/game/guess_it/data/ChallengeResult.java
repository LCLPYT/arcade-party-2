package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class ChallengeResult {

    private final Map<UUID, Integer> pointsGained = new HashMap<>();

    public void grant(ServerPlayerEntity player, int points) {
        pointsGained.put(player.getUuid(), points);
    }

    public int getPointsGained(ServerPlayerEntity player) {
        return pointsGained.getOrDefault(player.getUuid(), 0);
    }

    public Stream<Map.Entry<UUID, Integer>> orderedEntries() {
        return pointsGained.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());
    }

    public void clear() {
        pointsGained.clear();
    }
}

package work.lclpnet.ap2.game.pig_race.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.MiniGameHandle;

import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class PRProgress {

    private final MiniGameHandle gameHandle;
    @Getter
    private final SegmentedPath path;
    @Getter
    private final int rounds;
    private final Object2IntMap<UUID> playerRounds = new Object2IntOpenHashMap<>();
    private final List<Entry> ranking = new ArrayList<>();

    public PRProgress(MiniGameHandle gameHandle, SegmentedPath path, int rounds) {
        this.gameHandle = gameHandle;
        this.path = path;
        this.rounds = rounds;
    }

    public void update() {
        var ranking = new ArrayList<Entry>();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            double distance = getAbsoluteDistance(player);

            ranking.add(new Entry(player.getUuid(), distance));
        }

        ranking.sort(Comparator.comparingDouble(Entry::distance).reversed());

        synchronized (this) {
            this.ranking.clear();
            this.ranking.addAll(ranking);
        }
    }

    public synchronized double getFurthestAbsoluteDistance() {
        return ranking.isEmpty() ? 0 : ranking.getFirst().distance;
    }

    public synchronized List<ServerPlayerEntity> getRanking() {
        return ranking.stream()
                .map(Entry::uuid)
                .map(gameHandle.getParticipants()::getParticipant)
                .flatMap(Optional::stream)
                .toList();
    }

    public double getAbsoluteRemaining(ServerPlayerEntity player) {
        double remaining = 1 - getAbsoluteProgress(player);

        return remaining * path.getCombinedLength() * rounds;
    }

    public double getAbsoluteDistance(ServerPlayerEntity player) {
        return getAbsoluteProgress(player) * path.getCombinedLength() * rounds;
    }

    public double getAbsoluteProgress(ServerPlayerEntity player) {
        int round = getRound(player);

        double progress = path.getProgress(player);

        return max(0, min(rounds, (round - 1) + progress)) / rounds;
    }

    public int getRound(ServerPlayerEntity player) {
        return playerRounds.getOrDefault(player.getUuid(), 1);
    }

    public void incrementRound(ServerPlayerEntity player) {
        playerRounds.put(player.getUuid(), getRound(player) + 1);
    }

    record Entry(UUID uuid, double distance) {}
}

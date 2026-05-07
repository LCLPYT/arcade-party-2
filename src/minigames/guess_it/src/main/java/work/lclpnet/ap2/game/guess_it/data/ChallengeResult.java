package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChallengeResult {

    private final Map<UUID, Integer> pointsGained = new HashMap<>();
    private Object correctAnswer = null;

    public void grant(ServerPlayer player, int points) {
        pointsGained.put(player.getUUID(), points);
    }

    public int getPointsGained(ServerPlayer player) {
        return pointsGained.getOrDefault(player.getUUID(), 0);
    }

    public void clear() {
        pointsGained.clear();
        this.correctAnswer = null;
    }

    public void setCorrectAnswer(Object correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    @Nullable
    public Object getCorrectAnswer() {
        return correctAnswer;
    }

    public void grantIfCorrect(Iterable<ServerPlayer> participants, int correctResult,
                               Function<ServerPlayer, OptionalInt> choiceFunction) {
        for (ServerPlayer player : participants) {
            var optChoice = choiceFunction.apply(player);

            if (optChoice.isEmpty()) continue;

            int i = optChoice.getAsInt();

            // 3 points, if the answer is correct
            if (i == correctResult) {
                grant(player, 3);
            }
        }
    }

    public void grantClosest3(Collection<ServerPlayer> participants, int correctResult,
                              Function<ServerPlayer, OptionalInt> valueFunction) {
        grantClosest3Diff(participants, player -> {
            var value = valueFunction.apply(player);

            if (value.isEmpty()) return OptionalInt.empty();

            return OptionalInt.of(Math.abs(correctResult - value.getAsInt()));
        });
    }

    public void grantClosest3Diff(Collection<ServerPlayer> participants, Function<ServerPlayer, OptionalInt> diffFunction) {
        Map<ServerPlayer, Integer> absPlayerDiff = new HashMap<>(participants.size());

        // collect absolute difference to correct result for every player
        for (ServerPlayer player : participants) {
            OptionalInt diff = diffFunction.apply(player);

            if (diff.isPresent()) {
                absPlayerDiff.put(player, diff.getAsInt());
            }
        }

        // group by difference, sort by least off, select best 3
        var ordered = absPlayerDiff.entrySet().stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue))
                .entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .limit(3)
                .toList();

        // grant best 3 groups points based on their collective difference
        for (int i = 0; i < ordered.size(); i++) {
            var playerEntries = ordered.get(i).getValue();

            int points = 3 - i;

            for (var playerEntry : playerEntries) {
                ServerPlayer player = playerEntry.getKey();

                grant(player, points);
            }
        }
    }
}

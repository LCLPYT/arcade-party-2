package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChallengeResult {

    private final Map<UUID, Integer> pointsGained = new HashMap<>();
    private Object correctAnswer = null;

    public void grant(ServerPlayerEntity player, int points) {
        pointsGained.put(player.getUuid(), points);
    }

    public int getPointsGained(ServerPlayerEntity player) {
        return pointsGained.getOrDefault(player.getUuid(), 0);
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

    public void grantIfCorrect(Iterable<ServerPlayerEntity> participants, int correctResult,
                          Function<ServerPlayerEntity, OptionalInt> choiceFunction) {
        for (ServerPlayerEntity player : participants) {
            var optChoice = choiceFunction.apply(player);

            if (optChoice.isEmpty()) continue;

            int i = optChoice.getAsInt();

            // 3 points, if the answer is correct
            if (i == correctResult) {
                grant(player, 3);
            }
        }
    }

    public void grantClosest3(Collection<ServerPlayerEntity> participants, int correctResult,
                              Function<ServerPlayerEntity, OptionalInt> valueFunction) {
        grantClosest3Diff(participants, player -> {
            var value = valueFunction.apply(player);

            if (value.isEmpty()) return OptionalInt.empty();

            return OptionalInt.of(Math.abs(correctResult - value.getAsInt()));
        });
    }

    public void grantClosest3Diff(Collection<ServerPlayerEntity> participants, Function<ServerPlayerEntity, OptionalInt> diffFunction) {
        Map<ServerPlayerEntity, Integer> absPlayerDiff = new HashMap<>(participants.size());

        // collect absolute difference to correct result for every player
        for (ServerPlayerEntity player : participants) {
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
                ServerPlayerEntity player = playerEntry.getKey();

                grant(player, points);
            }
        }
    }
}

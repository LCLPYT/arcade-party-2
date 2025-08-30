package work.lclpnet.ap2.mode_default.util;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import lombok.Getter;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.Math.max;
import static java.util.stream.Collectors.toSet;

public class ScoreManager {

    private final IntScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new IntScoreDataContainer<>(PlayerRef::create);
    private final PlayerManager playerManager;
    @Getter
    private final int targetScore;
    private final Hook<Runnable> onChange = HookFactory.createArrayBacked(Runnable.class, hooks -> () -> {
        for (Runnable hook : hooks) {
            hook.run();
        }
    });
    @Getter
    private int round = 0;

    public ScoreManager(PlayerManager playerManager, int targetScore) {
        this.playerManager = playerManager;
        this.targetScore = targetScore;
    }

    public void setScore(PlayerRef player, int score) {
        data.setScore(player, max(0, score));

        onChange.invoker().run();
    }

    public void addScore(PlayerRef player, int score) {
        if (score <= 0) return;

        data.addScore(player, score);

        onChange.invoker().run();
    }

    public int getScore(PlayerRef ref) {
        return data.getScore(ref);
    }

    public void incrementRound() {
        round++;
    }

    public void decrementRound() {
        round = max(0, round - 1);
    }

    public Iterable<ObjectIntPair<PlayerRef>> iterateRankedScores() {
        return data::getRankedEntries;
    }

    public Stream<Set<ObjectIntPair<PlayerRef>>> streamEntriesRanked() {
        return data.streamEntriesRanked();
    }

    public boolean hasScores() {
        return !data.isEmpty();
    }

    /**
     * Get players with the best score, if the best score is at least the target score.
     * Resulting references may refer to offline players, who left the game early.
     * @return A stream of references to winners
     */
    public Stream<PlayerRef> getWinningPlayers() {
        return data.getBestScore()
                .filter(score -> score >= targetScore)
                .stream()
                .flatMap(bestScore -> data.streamOrderedEntries()
                        .filter(entry -> entry.score() == bestScore)
                        .map(DataEntry::subject));
    }

    public Stream<ServerPlayerEntity> getFinalists() {
        return getWinningPlayers()
                .map(PlayerRef::uuid)
                .map(playerManager::getPlayer)
                .filter(Objects::nonNull);
    }

    /**
     * Gets the final winner, if one exists.
     * If there is exactly one player with the highest score of at least the target score, that player is returned.
     * That winning player may be offline.
     * If there are multiple players with the highest score of at least the target score,
     * there will be no final winner yet, unless only one of them is online.
     * In that case that player will be the final winner.
     * @return The final winner, if one exists.
     */
    public Optional<PlayerRef> getFinalWinner() {
        Set<PlayerRef> winners = getWinningPlayers().collect(toSet());

        // there is exactly one winning player, may also possibly be offline right now
        if (winners.size() == 1) {
            return Optional.of(winners.iterator().next());
        }

        // check if there is exactly one online winning player among the possibly offline winners
        Set<ServerPlayerEntity> onlineWinners = getFinalists().collect(toSet());

        if (onlineWinners.size() == 1) {
            return Optional.of(onlineWinners.iterator().next())
                    .map(PlayerRef::create);
        }

        return Optional.empty();
    }

    /**
     * Checks if there is exactly one winner who should win the party.
     * The winning player may be offline.
     * If there are multiple winners, but only one is online, that player will be the final winner.
     * @return Whether
     */
    public boolean hasClearWinner() {
        return getFinalWinner().isPresent();
    }

    /**
     * @return Whether there are multiple online winners who have to participate in a finale to determine the final winner.
     */
    public boolean hasMultipleWinners() {
        return getFinalists().count() >= 2;
    }

    public Hook<Runnable> onChange() {
        return onChange;
    }

    public Optional<DataEntry<PlayerRef>> getEntry(PlayerRef ref) {
        return data.getEntry(ref);
    }
}

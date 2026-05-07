package work.lclpnet.ap2.impl.game.team;

import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.gaco.ds.IndexedSet;

import java.util.*;

import static work.lclpnet.ap2.impl.util.ColorUtil.squaredDistance;

public class ApTeams {
    /**
     * Returns a list of team keys with the most contrast between their team colors.
     * If the team colors were to be put on the color circle, each color would be spaced as far as possible from its neighbours.
     * @param key The base team key to compare the contrast against.
     * @param pool The team pool to choose from, including the key element.
     * @param amount The size of the returned set, including the base team key.
     * @return The list of team keys with complementary team colors.
     */
    public static <T extends TeamKey> List<T> complementary(T key, IndexedSet<T> pool, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Negative amount");
        }

        if (pool.size() < amount) {
            throw new IllegalArgumentException("Requested %s team keys, but only %s are registered".formatted(amount, pool.size()));
        }

        if (amount == 0) {
            return List.of();
        }

        List<T> selected = new ArrayList<>(amount);
        Set<T> available = new HashSet<>(pool);
        available.remove(key);
        selected.add(key);

        while (selected.size() < amount) {
            T next = available.stream()
                    .max(Comparator.comparingDouble(candidate -> selected.stream()
                            .mapToDouble(sel -> squaredDistance(candidate.color(), sel.color()))
                            .min()
                            .orElse(0)
                    ))
                    .orElseThrow();

            selected.add(next);
            available.remove(next);
        }

        return selected;
    }
}

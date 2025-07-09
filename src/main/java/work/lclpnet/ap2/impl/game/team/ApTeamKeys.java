package work.lclpnet.ap2.impl.game.team;

import net.minecraft.util.Formatting;
import work.lclpnet.ap2.api.game.team.TeamKey;
import work.lclpnet.ap2.impl.ds.IndexedSet;
import work.lclpnet.ap2.impl.util.ColorUtil;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static work.lclpnet.ap2.impl.util.ColorUtil.squaredDistance;

public class ApTeamKeys {

    private static final Map<String, TeamKey> BY_ID = new HashMap<>();
    private static final IndexedSet<TeamKey> TEAM_KEYS = new IndexedSet<>();

    public static final TeamKey
            RED = register(new RecordTeamKey("red", Formatting.RED)),
            BLUE = register(new RecordTeamKey("blue", Formatting.BLUE)),
            YELLOW = register(new RecordTeamKey("yellow", Formatting.YELLOW)),
            PURPLE = register(new RecordTeamKey("purple", Formatting.DARK_PURPLE));

    private static TeamKey register(TeamKey key) {
        if (BY_ID.containsKey(key.id())) {
            throw new IllegalArgumentException("Team id \"%s\" already exists".formatted(key.id()));
        }

        if (!TEAM_KEYS.add(key)) {
            throw new IllegalStateException("Team \"%s\" already exists".formatted(key));
        }

        BY_ID.put(key.id(), key);

        return key;
    }

    public static IndexedSet<TeamKey> teamKeys() {
        return IndexedSet.copyOf(TEAM_KEYS);
    }

    public static Optional<TeamKey> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /**
     * Returns a list of team keys with the most contrast between their team colors.
     * If the team colors were to be put on the color circle, each color would be spaced as far as possible from its neighbours.
     * @param key The base team key to compare the contrast against.
     * @param amount The size of the returned set, including the base team key.
     * @return The list of team keys with complementary team colors.
     */
    public static List<TeamKey> complementary(TeamKey key, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Negative amount");
        }

        if (TEAM_KEYS.size() < amount) {
            throw new IllegalArgumentException("Requested %s team keys, but only %s are registered".formatted(amount, TEAM_KEYS.size()));
        }

        if (amount == 0) {
            return List.of();
        }

        List<TeamKey> selected = new ArrayList<>(amount);
        Set<TeamKey> available = new HashSet<>(TEAM_KEYS);
        available.remove(key);
        selected.add(key);

        while (selected.size() < amount) {
            TeamKey next = available.stream()
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

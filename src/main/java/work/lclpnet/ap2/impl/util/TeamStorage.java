package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.team.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TeamStorage<T> {

    private final Map<Team, T> storage = new HashMap<>();
    private final Function<Team, T> factory;

    private TeamStorage(Function<Team, T> factory, @Nullable Map<Team, T> initial) {
        this.factory = factory;

        if (initial != null) {
            storage.putAll(initial);
        }
    }

    public T get(Team team) {
        return get(team, factory);
    }

    public T get(Team team, Supplier<T> supplier) {
        return get(team, t -> supplier.get());
    }

    public T get(Team team, Function<Team, T> factory) {
        return storage.computeIfAbsent(team, factory);
    }

    public Optional<T> optional(Team team) {
        return Optional.ofNullable(storage.get(team));
    }

    public static <T> TeamStorage<T> create(Function<Team, T> factory) {
        return new TeamStorage<>(factory, null);
    }

    public static <T> TeamStorage<T> create(Supplier<T> supplier) {
        return new TeamStorage<>(team -> supplier.get(), null);
    }

    public static <T> TeamStorage<T> ofFixed(Map<Team, T> values) {
        return new TeamStorage<>(team -> {
            throw new UnsupportedOperationException("Default factory is undefined");
        }, values);
    }
}

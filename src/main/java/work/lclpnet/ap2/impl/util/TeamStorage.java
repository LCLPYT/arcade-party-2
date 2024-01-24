package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.team.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

    public T getOrCreate(Team team) {
        return getOrCreate(team, factory);
    }

    public T getOrCreate(Team team, Supplier<T> supplier) {
        return getOrCreate(team, t -> supplier.get());
    }

    public T getOrCreate(Team team, Function<Team, T> factory) {
        return storage.computeIfAbsent(team, factory);
    }

    public T require(Team team) {
        return Objects.requireNonNull(storage.get(team), "No value is stored for team " + team.getKey().id());
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

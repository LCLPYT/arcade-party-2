package work.lclpnet.ap2.api.ds;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public interface Resolvable<T> {

    Resolvable<Void> NONE = () -> null;

    T resolve();

    default Optional<T> optional() {
        return Optional.ofNullable(resolve());
    }

    @SuppressWarnings("unchecked")
    static <T> Resolvable<@Nullable T> none() {
        return (Resolvable<T>) NONE;
    }

    static <T> Resolvable<T> constant(T t) {
        return t == null ? none() : () -> t;
    }
}

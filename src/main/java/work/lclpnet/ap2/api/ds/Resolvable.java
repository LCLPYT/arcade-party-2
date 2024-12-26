package work.lclpnet.ap2.api.ds;

import org.jetbrains.annotations.Nullable;

public interface Resolvable<T> {

    Resolvable<Void> NONE = () -> null;

    T resolve();

    @SuppressWarnings("unchecked")
    static <T> Resolvable<@Nullable T> none() {
        return (Resolvable<T>) NONE;
    }

    static <T> Resolvable<T> constant(T t) {
        return () -> t;
    }
}

package work.lclpnet.ap2.impl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Lazy<T, U> {

    private final Function<U, T> factory;
    private volatile T value;
    private List<Consumer<T>> actions = null;

    public Lazy(Function<U, T> factory) {
        this.factory = factory;
    }

    public T get(Supplier<U> supplier) {
        if (value != null) return value;

        synchronized (this) {
            if (value == null) {
                value = factory.apply(supplier.get());

                if (actions != null) {
                    actions.forEach(action -> action.accept(value));
                }
            }
        }

        return value;
    }

    public void afterEvaluate(Consumer<T> action) {
        Objects.requireNonNull(action);

        if (value == null) {
            synchronized (this) {
                if (value == null) {
                    if (actions == null) actions = new ArrayList<>();

                    actions.add(action);
                    return;
                }
            }
        }

        action.accept(value);
    }
}

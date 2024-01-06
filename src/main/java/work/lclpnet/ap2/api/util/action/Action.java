package work.lclpnet.ap2.api.util.action;

import work.lclpnet.kibu.hook.Registrable;

public interface Action<T> {

    Action<?> NOOP = action -> {};

    void then(T action);

    static <T> Action<T> create(Registrable<T> registrable) {
        return registrable::register;
    }

    @SuppressWarnings("unchecked")
    static <T> Action<T> noop() {
        return (Action<T>) NOOP;
    }
}

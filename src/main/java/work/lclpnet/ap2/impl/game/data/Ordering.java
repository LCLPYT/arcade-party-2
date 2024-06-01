package work.lclpnet.ap2.impl.game.data;

import java.util.Comparator;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

public enum Ordering {

    DESCENDING,
    ASCENDING;

    public OptionalInt best(IntStream stream) {
        return switch (this) {
            case DESCENDING -> stream.max();
            case ASCENDING -> stream.min();
        };
    }

    public Ordering opposite() {
        return switch (this) {
            case DESCENDING -> ASCENDING;
            case ASCENDING -> DESCENDING;
        };
    }

    public <T> Comparator<T> order(ToIntFunction<T> keyExtractor) {
        var comparator = Comparator.comparingInt(keyExtractor);

        return switch (this) {
            case DESCENDING -> comparator.reversed();
            case ASCENDING -> comparator;
        };
    }
}

package work.lclpnet.ap2.api.util;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param history Elements appear in the order they occurred, i.e. last element first, most recent element last.
 * @param <T> The queue element type.
 */
public record QueueTransfer<T>(List<T> history, Set<T> occurred) {

    private static final QueueTransfer<?> EMPTY = new QueueTransfer<>(List.of(), Set.of());

    @SuppressWarnings("unchecked")
    public static <T> QueueTransfer<T> empty() {
        return (QueueTransfer<T>) EMPTY;
    }

    public <U> QueueTransfer<U> map(Function<T, U> mapper) {
        if (this.equals(EMPTY)) {
            return empty();
        }

        return new QueueTransfer<>(
                history.stream().map(mapper).filter(Objects::nonNull).toList(),
                occurred.stream().map(mapper).filter(Objects::nonNull).collect(Collectors.toSet())
        );
    }
}

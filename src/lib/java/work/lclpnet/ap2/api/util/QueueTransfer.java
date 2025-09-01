package work.lclpnet.ap2.api.util;

import java.util.List;

/**
 * @param history Elements appear in the order they occurred, i.e. last element first, most recent element last.
 * @param <T> The queue element type.
 */
public record QueueTransfer<T>(List<T> history) {

    private static final QueueTransfer<?> EMPTY = new QueueTransfer<>(List.of());

    @SuppressWarnings("unchecked")
    public static <T> QueueTransfer<T> empty() {
        return (QueueTransfer<T>) EMPTY;
    }
}

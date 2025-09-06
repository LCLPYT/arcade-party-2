package work.lclpnet.ap2.impl.util;

import work.lclpnet.ap2.api.util.QueuePersistence;
import work.lclpnet.ap2.api.util.QueueTransfer;

public final class VoidQueuePersistence<T> implements QueuePersistence<T> {

    private static final VoidQueuePersistence<?> INSTANCE = new VoidQueuePersistence<>();

    private VoidQueuePersistence() {}

    @Override
    public QueueTransfer<T> restore() {
        return QueueTransfer.empty();
    }

    @Override
    public void store(QueueTransfer<T> transfer) {}

    @SuppressWarnings("unchecked")
    public static <T> VoidQueuePersistence<T> instance() {
        return (VoidQueuePersistence<T>) INSTANCE;
    }
}

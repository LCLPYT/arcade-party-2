package work.lclpnet.ap2.api.util;

public interface QueuePersistence<T> {

    QueueTransfer<T> restore();

    void store(QueueTransfer<T> transfer);
}

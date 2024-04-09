package work.lclpnet.ap2.impl.util;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

public class FilterIterator<T> implements Iterator<T> {
    private final Iterator<T> parent;
    private final Predicate<T> filter;
    private T next = null;

    public FilterIterator(Iterator<T> parent, Predicate<T> filter) {
        this.parent = parent;
        this.filter = filter;
        findNext();
    }

    private void findNext() {
        while (parent.hasNext()) {
            T element = parent.next();

            if (filter.test(element)) {
                next = element;
                return;
            }
        }

        next = null;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public T next() {
        T elem = Objects.requireNonNull(next, "No more elements");

        findNext();

        return elem;
    }
}

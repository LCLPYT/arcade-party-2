package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class IndexedSet<E> extends AbstractSet<E> {

    private final List<E> index = new ArrayList<>();
    private final Map<E, Integer> reverseIndex = new HashMap<>();

    @Override
    public @NotNull Iterator<E> iterator() {
        Iterator<E> parent = index.iterator();

        return new Iterator<>() {
            E prev = null;

            @Override
            public boolean hasNext() {
                return parent.hasNext();
            }

            @Override
            public E next() {
                return prev = parent.next();
            }

            @Override
            public void remove() {
                if (prev == null) {
                    throw new IllegalStateException();
                }

                IndexedSet.this.remove(prev);

                prev = null;
            }
        };
    }

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public boolean add(E e) {
        synchronized (this) {
            if (reverseIndex.containsKey(e)) return false;

            int idx = index.size();
            index.add(e);
            reverseIndex.put(e, idx);
            return true;
        }
    }

    @Override
    public boolean remove(Object o) {
        synchronized (this) {
            Integer idx = reverseIndex.remove(o);

            if (idx == null) return false;

            remove(idx.intValue());

            return true;
        }
    }

    public E remove(int idx) {
        synchronized (this) {
            E element = index.get(idx);

            reverseIndex.remove(element);

            // to prevent updating all following indexes in the reverseIndex, just swap the last element to idx
            int lastIdx = index.size() - 1;
            E lastElement = index.remove(lastIdx);

            // swap if idx is not the last element
            if (idx < lastIdx) {
                reverseIndex.put(lastElement, idx);
                index.set(idx, lastElement);
            }

            return element;
        }
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public boolean contains(Object o) {
        synchronized (this) {
            return reverseIndex.containsKey(o);
        }
    }

    public E get(int idx) {
        return index.get(idx);
    }
}

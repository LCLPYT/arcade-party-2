package work.lclpnet.ap2.impl.ds;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class IndexedSet<E> extends AbstractSet<E> {

    private final List<E> index;
    private final Map<E, Integer> reverseIndex;

    public IndexedSet() {
        this(16);
    }

    public IndexedSet(int initialCapacity) {
        index = new ArrayList<>(initialCapacity);
        reverseIndex = new HashMap<>(initialCapacity);
    }

    public IndexedSet(Collection<E> src) {
        this(src.size());
        addAll(src);
    }

    public IndexedSet(IndexedSet<E> src) {
        index = new ArrayList<>(src.index);
        reverseIndex = new HashMap<>(src.reverseIndex);
    }

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

    @Override
    public void clear() {
        synchronized (this) {
            index.clear();
            reverseIndex.clear();
        }
    }

    @SuppressWarnings("unchecked")
    public static <E> IndexedSet<E> copyOf(IndexedSet<E> src) {
        if (src instanceof IndexedSet.Immutable<E>) {
            return src;
        }

        if (src.isEmpty()) {
            return (IndexedSet<E>) Immutable.EMPTY;
        }

        return new Immutable<>(src);
    }

    public static final class Immutable<E> extends IndexedSet<E> {

        public static final Immutable<?> EMPTY = new Immutable<>();

        public Immutable() {
            super(0);
        }

        public Immutable(IndexedSet<E> src) {
            super(src);
        }

        @Override
        public boolean add(E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public E remove(int idx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }
    }
}

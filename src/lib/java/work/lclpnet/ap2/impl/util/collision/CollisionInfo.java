package work.lclpnet.ap2.impl.util.collision;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.util.Collider;

import java.util.*;

public class CollisionInfo implements Iterable<Collider> {

    private final Collection<Collider> collisions;

    public CollisionInfo(int expectedCollisions) {
        this.collisions = new HashSet<>(expectedCollisions);
    }

    public void reset() {
        synchronized (this) {
            collisions.clear();
        }
    }

    public void add(Collider collider) {
        Objects.requireNonNull(collider);

        synchronized (this) {
            collisions.add(collider);
        }
    }

    /**
     * Find colliders that are not present in another {@link CollisionInfo}.
     * @param other {@link CollisionInfo} to check against.
     * @return An iterator of all colliders that are unique to this instance.
     */
    public Iterable<Collider> diff(CollisionInfo other) {
        var parent = iterator();

        return () -> new Iterator<Collider>() {
            Collider next = null;

            void advance() {
                while (parent.hasNext()) {
                    Collider parentNext = parent.next();

                    if (other.collisions.contains(parentNext)) continue;

                    next = parentNext;
                    break;
                }
            }

            @Override
            public boolean hasNext() {
                if (next == null) advance();
                return next != null;
            }

            @Override
            public Collider next() {
                Collider ret = next;
                next = null;
                return ret;
            }
        };
    }

    public void set(CollisionInfo other) {
        synchronized (this) {
            reset();

            for (Collider collider : other) {
                add(collider);
            }
        }
    }

    public int count() {
        return collisions.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollisionInfo that = (CollisionInfo) o;
        return Objects.equals(collisions, that.collisions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collisions);
    }

    @NotNull
    @Override
    public Iterator<Collider> iterator() {
        return collisions.iterator();
    }

    @Override
    public Spliterator<Collider> spliterator() {
        return collisions.spliterator();
    }
}

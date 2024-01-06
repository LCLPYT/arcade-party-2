package work.lclpnet.ap2.impl.util.collision;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.util.Collider;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class CollisionInfoTest {

    @Test
    void initialEmpty() {
        var info = new CollisionInfo(1);
        assertEquals(0, StreamSupport.stream(info.spliterator(), false).count());
    }

    @Test
    void initialCount() {
        var info = new CollisionInfo(1);
        assertEquals(0, info.count());
    }

    @Test
    void add() {
        var info = new CollisionInfo(1);

        Collider collider = mock();
        info.add(collider);

        assertEquals(List.of(collider), StreamSupport.stream(info.spliterator(), false).toList());
    }

    @Test
    void reset() {
        var info = new CollisionInfo(1);

        Collider collider = mock();
        info.add(collider);

        info.reset();

        assertEquals(0, StreamSupport.stream(info.spliterator(), false).count());
    }

    @Test
    void equalsSameOrder() {
        var foo = new CollisionInfo(2);
        var bar = new CollisionInfo(2);

        Collider a = mock(), b = mock();

        foo.add(a);
        foo.add(b);

        bar.add(a);
        bar.add(b);

        assertEquals(foo, bar);
        assertEquals(bar, foo);
    }

    @Test
    void equalsDifferentOrder() {
        var foo = new CollisionInfo(2);
        var bar = new CollisionInfo(2);

        Collider a = mock(), b = mock();

        foo.add(a);
        foo.add(b);

        bar.add(b);
        bar.add(a);

        assertEquals(foo, bar);
        assertEquals(bar, foo);
    }

    @Test
    void equalsNotEquals() {
        var foo = new CollisionInfo(1);
        var bar = new CollisionInfo(2);

        Collider a = mock(), b = mock();

        foo.add(a);

        bar.add(b);
        bar.add(a);

        assertNotEquals(foo, bar);
        assertNotEquals(bar, foo);
    }

    @Test
    void diff() {
        var foo = new CollisionInfo(3);
        var bar = new CollisionInfo(2);

        Collider a = mock(), b = mock(), c = mock(), d = mock();

        foo.add(a);
        foo.add(c);
        foo.add(b);

        bar.add(c);
        bar.add(d);

        var diff = StreamSupport.stream(foo.diff(bar).spliterator(), false).toList();

        assertEquals(List.of(a, b), diff);
    }

    @Test
    void diffNotCommutative() {
        var foo = new CollisionInfo(3);
        var bar = new CollisionInfo(2);

        Collider a = mock(), b = mock(), c = mock(), d = mock();

        foo.add(a);
        foo.add(c);
        foo.add(b);

        bar.add(c);
        bar.add(d);

        var diff = StreamSupport.stream(bar.diff(foo).spliterator(), false).toList();

        assertEquals(List.of(d), diff);
    }

    @Test
    void set() {
        var foo = new CollisionInfo(1);
        var bar = new CollisionInfo(2);

        Collider a = mock(), b = mock(), c = mock();

        foo.add(c);

        bar.add(a);
        bar.add(b);

        foo.set(bar);

        assertEquals(bar.count(), foo.count());
        assertEquals(StreamSupport.stream(bar.spliterator(), false).toList(),
                StreamSupport.stream(foo.spliterator(), false).toList());
    }
}
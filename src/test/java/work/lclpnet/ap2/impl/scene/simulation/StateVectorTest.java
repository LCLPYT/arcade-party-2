package work.lclpnet.ap2.impl.scene.simulation;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateVectorTest {

    @Test
    void add() {
        var first = new StateVector(new Vector3d[]{
                new Vector3d(0, 0, 0),
                new Vector3d(1, 2, 3)
        });

        var second = new StateVector(new Vector3d[]{
                new Vector3d(-3, -2, -1),
                new Vector3d(1, 1, 1)
        });

        first.add(second);

        assertEquals(new Vector3d(-3, -2, -1), first.getVector3(0));
        assertEquals(new Vector3d(2, 3, 4), first.getVector3(1));
    }

    @Test
    void mul() {
        var state = new StateVector(new Vector3d[]{
                new Vector3d(1, 2, 3),
                new Vector3d(4, 5, 6)
        });

        state.mul(2);

        assertEquals(new Vector3d(2, 4, 6), state.getVector3(0));
        assertEquals(new Vector3d(8, 10, 12), state.getVector3(1));
    }

    @Test
    void copy() {
        var state = new StateVector(new Vector3d[]{
                new Vector3d(1, 2, 3),
                new Vector3d(4, 5, 6)
        });

        StateVector copy = state.copy();

        assertEquals(state.size(), copy.size());

        assertNotSame(state.getVector3(0), copy.getVector3(0));
        assertNotSame(state.getVector3(1), copy.getVector3(1));

        state.mul(5);

        assertNotEquals(state.getVector3(0), copy.getVector3(0));
        assertNotEquals(state.getVector3(1), copy.getVector3(1));
    }

    @Test
    void getVector3() {
        Vector3d v = new Vector3d(1, 2, 3);
        Vector3d w = new Vector3d(4, 5, 6);

        var state = new StateVector(new Vector3d[] { v, w });

        assertSame(v, state.getVector3(0));
        assertSame(w, state.getVector3(1));
    }

    @Test
    void getVector3_outOfBounds_throws() {
        var state = new StateVector(new Vector3d[] {
                new Vector3d(1, 2, 3),
                new Vector3d(4, 5, 6)
        });

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> state.getVector3(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> state.getVector3(2));
    }
}
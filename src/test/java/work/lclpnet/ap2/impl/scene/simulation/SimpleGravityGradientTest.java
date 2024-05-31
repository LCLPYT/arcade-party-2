package work.lclpnet.ap2.impl.scene.simulation;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.scene.simulation.solver.RungeKuttaSolver;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static work.lclpnet.ap2.impl.util.AssertionUtils.assertVectorsEqual;

class SimpleGravityGradientTest {

    @Test
    void testSimple() {
        var state = new StateVector(new Vector3d[]{
                new Vector3d(0, 0, 0),  // position
                new Vector3d(0, 0, 0),  // velocity
        });

        var gradient = new SimpleGravityGradient(2);

        // verify positions in intervals of 0.1 seconds
        assertY(state, gradient, -0.01);  // t=0.1
        assertY(state, gradient, -0.04);  // t=0.2
        assertY(state, gradient, -0.09);  // t=0.3
        assertY(state, gradient, -0.16);  // t=0.4
        assertY(state, gradient, -0.25);  // t=0.5
        assertY(state, gradient, -0.36);  // t=0.6
        assertY(state, gradient, -0.49);  // t=0.7
        assertY(state, gradient, -0.64);  // t=0.8
        assertY(state, gradient, -0.81);  // t=0.9
        assertY(state, gradient, -1.00);  // t=1.0
        assertY(state, gradient, -1.21);  // t=1.1
        assertY(state, gradient, -1.44);  // t=1.2
        assertY(state, gradient, -1.69);  // t=1.3
        assertY(state, gradient, -1.96);  // t=1.4
        assertY(state, gradient, -2.25);  // t=1.5
        assertY(state, gradient, -2.56);  // t=1.6
        assertY(state, gradient, -2.89);  // t=1.7
        assertY(state, gradient, -3.24);  // t=1.8
        assertY(state, gradient, -3.61);  // t=1.9
        assertY(state, gradient, -4.00);  // t=2.0
    }

    private static void assertY(StateVector state, Gradient gradient, double y) {
        RungeKuttaSolver.INSTANCE.solve(state, 0.1, gradient);
        assertVectorsEqual(new Vector3d(0, y, 0), state.getVector3(0));
    }

    @Test
    void getLaunchVelocity() {
        var from = new Vector3d(0, 66, 0);
        var to = new Vector3d(2, 60, 2);
        double grav = 9.81;

        var gradient = new SimpleGravityGradient(grav);
        Vector3d velocity = gradient.getLaunchVelocity(from, to, 3);

        var state = new StateVector(new Vector3d[]{
                new Vector3d(from),
                velocity,
        });

        for (int i = 0; i < 1000; i++) {
            RungeKuttaSolver.INSTANCE.solve(state, 0.05, gradient);

            Vector3d pos = state.getVector3(0);

            double distanceSq = pos.distanceSquared(to);

            if (distanceSq <= 0.2) return;
        }

        fail("Trajectory did not reach target position");
    }

    @Test
    void getLaunchVelocity_heightTooSmall_throws() {
        var from = new Vector3d(0, 5, 0);
        var to = new Vector3d(0, 10, 0);

        var gradient = new SimpleGravityGradient(9.81);
        assertThrows(IllegalArgumentException.class, () -> gradient.getLaunchVelocity(from, to, 4));
    }
}
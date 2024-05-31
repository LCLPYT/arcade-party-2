package work.lclpnet.ap2.impl.scene.simulation.solver;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.scene.simulation.Gradient;
import work.lclpnet.ap2.impl.scene.simulation.StateVector;

import static work.lclpnet.ap2.impl.util.AssertionUtils.assertVectorsEqual;

class EulerSolverTest {

    @Test
    void solveSimpleHarmonicOscillator() {
        StateVector state = new StateVector(new Vector3d[]{
                new Vector3d(1, 0, 0),  // pos
                new Vector3d(0, 0, 0)   // vel
        });

        Gradient gradient = prev -> new StateVector(new Vector3d[]{
                new Vector3d(prev.getVector3(1)),
                new Vector3d(prev.getVector3(0)).mul(-1)
        });

        EulerSolver.INSTANCE.solve(state, 0.1, gradient);

        assertVectorsEqual(new Vector3d(1, 0, 0), state.getVector3(0));
        assertVectorsEqual(new Vector3d(-0.1, 0, 0), state.getVector3(1));
    }

    @Test
    void testZeroInitialConditions() {
        StateVector state = new StateVector(new Vector3d[]{
                new Vector3d(0, 0, 0),  // pos
                new Vector3d(0, 0, 0)   // vel
        });

        Gradient gradient = prev -> new StateVector(new Vector3d[]{
                new Vector3d(),
                new Vector3d()
        });

        EulerSolver.INSTANCE.solve(state, 0.1, gradient);

        assertVectorsEqual(new Vector3d(0, 0, 0), state.getVector3(0));
        assertVectorsEqual(new Vector3d(0, 0, 0), state.getVector3(1));
    }

    @Test
    void testZeroTimeStep() {
        StateVector state = new StateVector(new Vector3d[]{
                new Vector3d(1, 0, 0),    // pos
                new Vector3d(1.5, 0, 0)   // vel
        });

        Gradient gradient = prev -> new StateVector(new Vector3d[]{
                new Vector3d(prev.getVector3(1)),
                new Vector3d(prev.getVector3(0)).mul(-1)
        });

        EulerSolver.INSTANCE.solve(state, 0, gradient);

        assertVectorsEqual(new Vector3d(1, 0, 0), state.getVector3(0));
        assertVectorsEqual(new Vector3d(1.5, 0, 0), state.getVector3(1));
    }
}
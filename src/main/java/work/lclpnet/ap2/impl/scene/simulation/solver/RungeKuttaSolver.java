package work.lclpnet.ap2.impl.scene.simulation.solver;

import work.lclpnet.ap2.impl.scene.simulation.Gradient;
import work.lclpnet.ap2.impl.scene.simulation.StateVector;

/**
 * A fourth order method for solving ODEs.
 * @see <a href="https://en.wikipedia.org/wiki/Runge%E2%80%93Kutta_methods">Runge-Kutta on Wikipedia</a>.
 */
public class RungeKuttaSolver implements NumericalSolver {

    public static final RungeKuttaSolver INSTANCE = new RungeKuttaSolver();

    private RungeKuttaSolver() {}

    @Override
    public void solve(StateVector state, double dt, Gradient gradient) {
        StateVector f0 = gradient.apply(state);
        StateVector half0 = state.copy().add(f0.copy().mul(dt * 0.5));
        StateVector f1 = gradient.apply(half0);
        StateVector half1 = state.copy().add(f1.copy().mul(dt * 0.5));
        StateVector f2 = gradient.apply(half1);
        StateVector predictedNext = state.copy().add(f2.copy().mul(dt));
        StateVector f3 = gradient.apply(predictedNext);

        StateVector weighted = f0.add(f1.mul(2)).add(f2.mul(2)).add(f3);

        state.add(weighted.mul(dt / 6));
    }
}

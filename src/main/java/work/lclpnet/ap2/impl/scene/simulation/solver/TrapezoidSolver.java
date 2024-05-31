package work.lclpnet.ap2.impl.scene.simulation.solver;

import work.lclpnet.ap2.impl.scene.simulation.Gradient;
import work.lclpnet.ap2.impl.scene.simulation.StateVector;

/**
 * A second order method to solve ODEs.
 * @see <a href="https://en.wikipedia.org/wiki/Trapezoidal_rule_(differential_equations)">Trapezoidal rule on Wikipedia</a>
 */
public class TrapezoidSolver implements NumericalSolver {

    public static final TrapezoidSolver INSTANCE = new TrapezoidSolver();

    private TrapezoidSolver() {}

    @Override
    public void solve(StateVector state, double dt, Gradient gradient) {
        StateVector f0 = gradient.apply(state);
        StateVector predictedNext = state.copy().add(f0.copy().mul(dt));
        StateVector f1 = gradient.apply(predictedNext);

        state.add(f0.add(f1).mul(dt * 0.5));
    }
}

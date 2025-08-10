package work.lclpnet.ap2.impl.scene.simulation.solver;

import work.lclpnet.ap2.impl.scene.simulation.Gradient;
import work.lclpnet.ap2.impl.scene.simulation.StateVector;

/**
 * A first order method for solving ODEs.
 * @see <a href="https://en.wikipedia.org/wiki/Euler_method">Euler method on Wikipedia</a>
 */
public class EulerSolver implements NumericalSolver {

    public static final EulerSolver INSTANCE = new EulerSolver();

    private EulerSolver() {}

    @Override
    public void solve(StateVector state, double dt, Gradient gradient) {
        state.add(gradient.apply(state).mul(dt));
    }
}

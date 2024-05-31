package work.lclpnet.ap2.impl.scene.simulation.solver;

import work.lclpnet.ap2.impl.scene.simulation.Gradient;
import work.lclpnet.ap2.impl.scene.simulation.StateVector;

/**
 * A solver for ordinary differential equations.
 */
public interface NumericalSolver {

    /**
     * Advances the state by a discrete time interval.
     * @param state The current state vector. It will be mutated so that it is the new state afterward.
     * @param dt The discrete time interval.
     * @param gradient The gradient.
     */
    void solve(StateVector state, double dt, Gradient gradient);
}

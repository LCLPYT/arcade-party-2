package work.lclpnet.ap2.impl.util.math.shape;

import static java.lang.Math.*;

public class Shapes {

    public static double chebyshevDelta(double dx, double dy, double dz) {
        return max(abs(dx), max(abs(dy), abs(dz)));
    }

    public static double euclideanDelta(double dx, double dy, double dz) {
        return sqrt(dx * dx + dy * dy + dz * dz);
    }
}

package work.lclpnet.ap2.impl.util;

import lombok.Getter;
import net.minecraft.util.math.Vec3d;
import org.ejml.data.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.map.MapUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.Math.floor;
import static java.lang.Math.min;

public class SplinePath {

    @Getter
    private final List<Vec3d> keypoints;
    private final double[] ts;
    private final double[] x, y, z;
    private final double[] mX, mY, mZ;  // d^2 / dx^2
    private final int n;

    protected SplinePath(List<Vec3d> keypoints) throws SingularMatrixException {
        this.keypoints = List.copyOf(keypoints);

        n = keypoints.size();

        if (n < 2) {
            throw new IllegalArgumentException("Too few keypoints, at least 2 keypoints are required");
        }

        ts = new double[n];
        x = new double[n];
        y = new double[n];
        z = new double[n];

        for (int i = 0; i < n; i++) {
            ts[i] = i;

            Vec3d p = keypoints.get(i);

            x[i] = p.getX();
            y[i] = p.getY();
            z[i] = p.getZ();
        }

        mX = solveNaturalCubic(ts, x);
        mY = solveNaturalCubic(ts, y);
        mZ = solveNaturalCubic(ts, z);
    }

    public Vec3d sample(double t) {
        if (t <= 0) {
            return new Vec3d(x[0], y[0], z[0]);
        }

        if (t >= ts[n - 1]) {
            return new Vec3d(x[n - 1], y[n - 1], z[n - 1]);
        }

        int i = min(n-2, (int) floor(t));

        double h = ts[i + 1] - ts[i];
        double a = (ts[i + 1] - t) / h;
        double b = (t - ts[i]) / h;

        double h2 = h * h;

        double a3ma = a * a * a - a;
        double b3mb = b * b * b - b;

        double xs = a * x[i] + b * x[i + 1] + (a3ma * mX[i] + b3mb * mX[i + 1]) * h2 / 6.d;
        double ys = a * y[i] + b * y[i + 1] + (a3ma * mY[i] + b3mb * mY[i + 1]) * h2 / 6.d;
        double zs = a * z[i] + b * z[i + 1] + (a3ma * mZ[i] + b3mb * mZ[i + 1]) * h2 / 6.d;

        return new Vec3d(xs, ys, zs);
    }

    private static double[] solveNaturalCubic(double[] t, double[] v) {
        final int n = t.length;

        var A = new SimpleMatrix(n, n);
        var rhs = new SimpleMatrix(n, 1);

        A.set(0, 0, 1);
        A.set(n - 1, n - 1, 1);
        rhs.set(0, 0, 0);
        rhs.set(n - 1, 0, 0);

        for (int i = 1; i < n - 1; i++) {
            double h0 = t[i] - t[i - 1];
            double h1 = t[i + 1] - t[i];

            A.set(i, i - 1, h0);
            A.set(i, i, 2 * (h0 + h1));
            A.set(i, i + 1, h1);

            double dv = (v[i + 1] - v[i]) / h1 - (v[i] - v[i - 1]) / h0;

            rhs.set(i, 0, 6 * dv);
        }

        return solve(A, rhs, n);
    }

    private static double[] solve(SimpleMatrix A, SimpleMatrix rhs, int n) {
        SimpleMatrix m = A.solve(rhs);

        double[] out = new double[n];

        for (int i = 0; i < n; i++) {
            out[i] = m.get(i, 0);
        }

        return out;
    }

    public static Optional<SplinePath> readCentered(JSONArray json, Logger logger) {
        List<Vec3d> keypoints = new ArrayList<>(json.length());

        for (Object item : json) {
            if (!(item instanceof JSONArray tuple)) {
                logger.error("Invalid dragon path element: {}", item);
                continue;
            }

            keypoints.add(MapUtil.readCenteredVec3d(tuple));
        }

        return create(keypoints, logger);
    }

    public static Optional<SplinePath> create(List<Vec3d> keypoints, Logger logger) {
        if (keypoints.size() < 2) {
            logger.error("Too few keypoints to create a spline path");
            return Optional.empty();
        }

        try {
            return Optional.of(new SplinePath(keypoints));
        } catch (SingularMatrixException e) {
            logger.error("Failed to solve matrix", e);
            return Optional.empty();
        } catch (Throwable t) {
            logger.error("Failed to create spline path", t);
            return Optional.empty();
        }
    }
}

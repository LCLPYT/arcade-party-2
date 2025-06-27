package work.lclpnet.ap2.impl.util;

import net.minecraft.util.math.Vec3d;
import org.ejml.data.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;
import org.json.JSONArray;
import org.slf4j.Logger;
import work.lclpnet.ap2.impl.map.MapUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static java.lang.Math.*;

public class SplinePath {

    private final double[] x, y, z;
    private final double[] d2x, d2y, d2z;  // d^2 / dx^2 at keypoints
    private final double[] arcLength;
    private final int n;

    protected SplinePath(List<Vec3d> keypoints) throws SingularMatrixException {
        n = keypoints.size();

        if (n < 2) {
            throw new IllegalArgumentException("Too few keypoints, at least 2 keypoints are required");
        }

        x = new double[n];
        y = new double[n];
        z = new double[n];

        for (int i = 0; i < n; i++) {
            Vec3d p = keypoints.get(i);

            x[i] = p.getX();
            y[i] = p.getY();
            z[i] = p.getZ();
        }

        d2x = solveNaturalCubic(n, x);
        d2y = solveNaturalCubic(n, y);
        d2z = solveNaturalCubic(n, z);

        arcLength = buildArcLength(n * 40);
    }

    private double[] buildArcLength(int samples) {
        final double dt = 1.d / (samples - 1);

        double[] arcLength = new double[samples];

        Vec3d start = sampleRaw(0.d);
        double totalLen = 0.d;

        arcLength[0] = totalLen;

        for (int i = 1; i < samples; i++) {
            Vec3d end = sampleRaw(i * dt);

            totalLen += start.distanceTo(end);
            arcLength[i] = totalLen;

            start = end;
        }

        return arcLength;
    }

    public Vec3d sampleRaw(double t) {
        if (t <= 0.d) {
            return new Vec3d(x[0], y[0], z[0]);
        }

        if (t >= 1.d) {
            return new Vec3d(x[n - 1], y[n - 1], z[n - 1]);
        }

        t *= n - 1;

        int i = min(n - 2, (int) floor(t));

        double a = (i + 1) - t;
        double b = t - i;

        double a3ma = a * a * a - a;
        double b3mb = b * b * b - b;

        double xs = a * x[i] + b * x[i + 1] + (a3ma * d2x[i] + b3mb * d2x[i + 1]) / 6.d;
        double ys = a * y[i] + b * y[i + 1] + (a3ma * d2y[i] + b3mb * d2y[i + 1]) / 6.d;
        double zs = a * z[i] + b * z[i + 1] + (a3ma * d2z[i] + b3mb * d2z[i + 1]) / 6.d;

        return new Vec3d(xs, ys, zs);
    }

    public Vec3d sample(double s) {
        if (s <= 0.d) {
            return new Vec3d(x[0], y[0], z[0]);
        }

        if (s >= 1.d) {
            return new Vec3d(x[n - 1], y[n - 1], z[n - 1]);
        }

        double targetLength = s * arcLength[arcLength.length - 1];

        int i = min(findArcLengthSection(targetLength), arcLength.length - 1);

        double len0 = arcLength[i];
        double len1 = arcLength[i + 1];

        // determine progress between arc length samples (assume linear)
        double fraction = (targetLength - len0) / (len1 - len0 + 1e-10d);

        // fractional position in arcLength array
        double arcLengthPos = i + fraction;

        // convert to spline parameter
        double t = arcLengthPos / arcLength.length;

        return sampleRaw(t);
    }

    private int findArcLengthSection(double targetLength) {
        int i = Arrays.binarySearch(arcLength, targetLength);

        if (i >= 0) {
            return i;
        }

        // exact time is not in the array, use the previous sample (similar to floor())
        return max(0, -(i + 1) - 1);
    }

    public List<Vec3d> getKeypoints() {
        List<Vec3d> keypoints = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            keypoints.add(new Vec3d(x[i], y[i], z[i]));
        }

        return keypoints;
    }

    private static double[] solveNaturalCubic(int n, double[] v) {
        var A = new SimpleMatrix(n, n);
        var rhs = new SimpleMatrix(n, 1);

        A.set(0, 0, 1);
        A.set(n - 1, n - 1, 1);
        rhs.set(0, 0, 0);
        rhs.set(n - 1, 0, 0);

        for (int i = 1; i < n - 1; i++) {
            A.set(i, i - 1, 1.d);
            A.set(i, i, 2 * (1.d + 1.d));
            A.set(i, i + 1, 1.d);

            double dv = (v[i + 1] - v[i]) - (v[i] - v[i - 1]);

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
                logger.error("Invalid spline path element: {}", item);
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

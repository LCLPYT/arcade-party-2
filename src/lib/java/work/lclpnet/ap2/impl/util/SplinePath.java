package work.lclpnet.ap2.impl.util;

import net.minecraft.util.math.Vec3d;
import org.ejml.data.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
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

        Vec3d start = samplePositionLinear(0.d);
        double totalLen = 0.d;

        arcLength[0] = totalLen;

        for (int i = 1; i < samples; i++) {
            Vec3d end = samplePositionLinear(i * dt);

            totalLen += start.distanceTo(end);
            arcLength[i] = totalLen;

            start = end;
        }

        return arcLength;
    }

    /**
     * Samples a path position using linear segment-parametrization given a progress parameter t.
     * @param t The progress parameter ranging [0..1].
     * @return The position vector at t.
     */
    public Vec3d samplePositionLinear(double t) {
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

    /**
     * Samples the first derivative using linear segment-parametrization given a progress parameter t.
     * This represents the tangent vector or direction at that point.
     * @param t The progress parameter ranging [0..1].
     * @return The tangent vector at t.
     */
    public Vec3d sampleDirectionLinear(double t) {
        if (t <= 0.d) {
            return samplePositionLinear(0.001).subtract(samplePositionLinear(0)).normalize();
        }

        if (t >= 1.d) {
            return samplePositionLinear(1).subtract(samplePositionLinear(0.999)).normalize();
        }

        t *= n - 1;

        int i = min(n - 2, (int) floor(t));

        double a = (i + 1) - t;
        double b = t - i;

        double dxs = -x[i] + x[i + 1] + (-(3 * a * a - 1) * d2x[i] + (3 * b * b - 1) * d2x[i + 1]) / 6.d;
        double dys = -y[i] + y[i + 1] + (-(3 * a * a - 1) * d2y[i] + (3 * b * b - 1) * d2y[i + 1]) / 6.d;
        double dzs = -z[i] + z[i + 1] + (-(3 * a * a - 1) * d2z[i] + (3 * b * b - 1) * d2z[i + 1]) / 6.d;

        return new Vec3d(dxs * (n - 1), dys * (n - 1), dzs * (n - 1));
    }

    /**
     * Samples the second derivative using linear segment-parametrization given a progress parameter t.
     * This represents the change of direction or curvature at that point.
     * @param t The progress parameter ranging [0..1].
     * @return The curvature vector at t.
     */
    public @NotNull Vec3d sampleCurvatureLinear(double t) {
        double t_tau = t * (n - 1);

        int i = min(n - 2, (int) floor(t_tau));

        double a = (i + 1) - t_tau;
        double b = t_tau - i;

        double d2xs = (a * d2x[i] + b * d2x[i + 1]) * (n - 1) * (n - 1);
        double d2ys = (a * d2y[i] + b * d2y[i + 1]) * (n - 1) * (n - 1);
        double d2zs = (a * d2z[i] + b * d2z[i + 1]) * (n - 1) * (n - 1);

        return new Vec3d(d2xs, d2ys, d2zs);
    }

    /**
     * Samples a path position using arclength-normalized parametrization given a progress parameter s.
     * @param s The progress parameter ranging [0..1].
     * @return The position vector at s.
     */
    public Vec3d samplePosition(double s) {
        return samplePositionLinear(getLinearProgress(s));
    }

    /**
     * Samples the first derivative using arclength-normalized parametrization given a progress parameter s.
     * This represents the tangent vector or direction at that point.
     * @param s The progress parameter ranging [0..1].
     * @return The tangent vector at s.
     */
    public Vec3d sampleDirection(double s) {
        return sampleDirectionLinear(getLinearProgress(s));
    }

    /**
     * Samples the second derivative using arclength-normalized parametrization given a progress parameter s.
     * This represents the change of direction or curvature at that point.
     * @param s The progress parameter ranging [0..1].
     * @return The curvature vector at s.
     */
    public Vec3d sampleCurvature(double s) {
        return sampleCurvatureLinear(getLinearProgress(s));
    }

    /**
     * Return the spline parameter [0..1] that corresponds to the nearest point on the path towards the given position using arclength-normalized parametrization.
     * In other words, grade the progress of a given position along the path.
     * @param queryPos The query position.
     * @return The spline parameter [0..1] that can be used to retrieve the nearest path position towards
     * the query position using the {@link #samplePosition(double)} method.
     */
    public double getProgress(Vec3d queryPos) {
        double t = getLinearProgress(queryPos);

        return getProgress(t);
    }

    /**
     * Return the spline parameter [0..1] that corresponds to the nearest point on the path towards the given position using linear segment parametrization.
     * In other words, grade the progress of a given position along the path.
     * @param queryPos The query position.
     * @return The spline parameter [0..1] that can be used to retrieve the nearest path position towards
     * the query position using the {@link #samplePositionLinear(double)} method.
     */
    public double getLinearProgress(Vec3d queryPos) {
        // estimate segment progress, then refine using Newtons method
        double t = estimateSegmentProgress(queryPos);

        final int MAX_ITERATIONS = 50;
        final double EPSILON = 1e-6;

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            Vec3d pos = samplePositionLinear(t);
            Vec3d d1 = sampleDirectionLinear(t);  // first derivative

            Vec3d V = pos.subtract(queryPos);

            // f(t) = V . P'(t)
            double ft = V.dotProduct(d1);

            if (abs(ft) < EPSILON) break;

            // Calculate f'(t)
            Vec3d d2 = sampleCurvatureLinear(t);  // second derivative
            double d1t = d1.dotProduct(d1) + V.dotProduct(d2);

            if (abs(d1t) < 1e-10) break;

            // Newton update, clamp
            t = max(0.d, min(1.d, t - (ft / d1t)));
        }

        return t;
    }

    public Vec3d getNearestPosition(Vec3d queryPos) {
        double s = getLinearProgress(queryPos);

        return samplePositionLinear(s);
    }

    /**
     * Converts a given segment progress in the linear domain to a normalized progress in the arclength domain.
     * @param t The linear segment progress.
     * @return The arclength-normalized progress.
     */
    public double getProgress(double t) {
        if (t <= 0.d) return 0.d;
        if (t >= 1.d) return 1.d;

        double finalArcLength = sampleArcLength(t);

        return finalArcLength / arcLength[arcLength.length - 1];
    }

    /**
     * Converts a given arclength-normalized progress parameter to a linear segment progress parameter.
     * @param s The arclength-normalized progress.
     * @return The linear segment progress.
     */
    public double getLinearProgress(double s) {
        if (s <= 0.d) return 0.d;
        if (s >= 1.d) return 1.d;

        double targetLength = s * arcLength[arcLength.length - 1];
        int i = min(findArcLengthSection(arcLength, targetLength), arcLength.length - 1);

        double len0 = arcLength[i];
        double len1 = arcLength[i + 1];

        if (abs(len0 - len1) < 1e-10) {
            return (double) i / (arcLength.length - 1);
        }

        double fraction = (targetLength - len0) / (len1 - len0);

        double arcLengthPos = i + fraction;

        return arcLengthPos / (arcLength.length - 1);
    }

    /**
     * Samples a given linear segment progress to the corresponding arclength.
     * @param t The linear segment progress.
     * @return The corresponding arclength.
     */
    @VisibleForTesting
    protected double sampleArcLength(double t) {
        double arcLengthIndex = t * (arcLength.length - 1);
        int lowerIndex = (int) floor(arcLengthIndex);
        int upperIndex = (int) ceil(arcLengthIndex);

        if (lowerIndex == upperIndex) {
            return arcLength[lowerIndex];
        }

        double weightUpper = arcLengthIndex - lowerIndex;
        double weightLower = 1.0 - weightUpper;

        return arcLength[lowerIndex] * weightLower + arcLength[upperIndex] * weightUpper;
    }

    /**
     * Estimates the linear segment progress by finding the closest of discrete sampling points.
     * @param queryPos The query position.
     * @return The estimated progress
     */
    @VisibleForTesting
    protected double estimateSegmentProgress(Vec3d queryPos) {
        final double dt = 1.d / (arcLength.length - 1);

        // arcLength pos to spline parameter
        double minSqDist = Double.MAX_VALUE;
        double minT = 0.0;

        for (int i = 0; i < arcLength.length; i++) {
            double t = i * dt;

            Vec3d sample = samplePositionLinear(t);
            double distSq = queryPos.squaredDistanceTo(sample);

            if (distSq < minSqDist) {
                minSqDist = distSq;
                minT = t;
            }
        }

        return minT;
    }

    /**
     * Finds the lower index of the range in the cumulative arcLength array where targetLength is greater or equal
     * than the lower entry and smaller than the upper entry.
     * @param arcLength The cumulative arclength array.
     * @param targetLength The target length.
     * @return The index after which targetLength would be placed inside the arcLength array.
     */
    @VisibleForTesting
    protected static int findArcLengthSection(double[] arcLength, double targetLength) {
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

    public double getLength() {
        return arcLength[arcLength.length - 1];
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

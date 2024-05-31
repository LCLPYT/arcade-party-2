package work.lclpnet.ap2.impl.util;

import org.joml.Vector3dc;
import org.opentest4j.AssertionFailedError;

public class AssertionUtils {

    private AssertionUtils() {}

    public static void assertVectorsEqual(Vector3dc expected, Vector3dc actual) {
        assertVectorsEqual(expected, actual, 1e-6);
    }

    public static void assertVectorsEqual(Vector3dc expected, Vector3dc actual, double epsilon) {
        if (Math.abs(expected.x() - actual.x()) < epsilon) return;
        if (Math.abs(expected.y() - actual.y()) < epsilon) return;
        if (Math.abs(expected.z() - actual.z()) < epsilon) return;

        throw new AssertionFailedError("Vectors are not equal", expected, actual);
    }
}

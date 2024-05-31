package work.lclpnet.ap2.impl.scene.simulation;

import org.joml.Vector3d;

/**
 * A simple gradient that only considers gravity, independent of any other forces.
 */
public class SimpleGravityGradient implements Gradient {

    private final double gravityAcceleration;

    public SimpleGravityGradient(double gravityAcceleration) {
        this.gravityAcceleration = gravityAcceleration;
    }

    @Override
    public StateVector apply(StateVector current) {
        // assume StateVector has (position, velocity) for each element
        int elements = current.size() / 2;

        Vector3d[] vectors = new Vector3d[elements * 2];

        for (int i = 0; i < elements; i++) {
            int offset = i * 2;

            // velocity
            vectors[offset] = new Vector3d(current.getVector3(offset + 1));

            // acceleration
            vectors[offset + 1] = new Vector3d(0, -gravityAcceleration, 0);
        }

        return new StateVector(vectors);
    }

    public Vector3d getLaunchVelocity(Vector3d from, Vector3d to, double height) {
        double maxY = from.y + height;

        if (maxY < to.y) {
            throw new IllegalArgumentException("Height %f is too small to reach y=%f from y=%f".formatted(height, to.y, from.y));
        }

        // time to reach the highest point (equivalent to time to fall the same height on the other side)
        double t_up = Math.sqrt((2 * height) / gravityAcceleration);

        // time to reach targetPos.y from maxY
        double t_down = Math.sqrt((2 * (maxY - to.y)) / gravityAcceleration);

        double travelTime = t_up + t_down;

        return new Vector3d(
                (to.x - from.x) / travelTime,
                Math.sqrt(2 * gravityAcceleration * height),
                (to.z - from.z) / travelTime);
    }
}

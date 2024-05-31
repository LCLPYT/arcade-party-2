package work.lclpnet.ap2.impl.scene.simulation;

import org.joml.Vector3d;

public class StateVector {

    private final Vector3d[] vectors;

    public StateVector(Vector3d[] vectors) {
        this.vectors = vectors;
    }

    public StateVector add(StateVector other) {
        int len = this.vectors.length;
        int otherLen = other.vectors.length;

        if (len != otherLen) {
            throw new IllegalArgumentException("Vector dimensions differ: %s, %s".formatted(len, otherLen));
        }

        for (int i = 0; i < len; i++) {
            this.vectors[i].add(other.vectors[i]);
        }

        return this;
    }

    public StateVector mul(double scalar) {
        for (Vector3d vector : vectors) {
            vector.mul(scalar);
        }

        return this;
    }

    public StateVector copy() {
        Vector3d[] vectorCopies = new Vector3d[this.vectors.length];

        for (int i = 0; i < this.vectors.length; i++) {
            vectorCopies[i] = new Vector3d(this.vectors[i]);
        }

        return new StateVector(vectorCopies);
    }

    public Vector3d getVector3(int i) {
        return this.vectors[i];
    }

    public int size() {
        return this.vectors.length;
    }
}

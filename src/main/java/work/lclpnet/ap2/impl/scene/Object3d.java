package work.lclpnet.ap2.impl.scene;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Objects;

public class Object3d {

    public final Vector3d position = new Vector3d();
    public final Matrix4d matrix = new Matrix4d();
    public final Matrix4d matrixWorld = new Matrix4d();
    public final Quaterniond rotation = new Quaterniond();
    public final Vector3d scale = new Vector3d(1, 1, 1);
    private final Collection<Object3d> children = new ObjectArraySet<>();
    private Object3d parent = null;

    public void updateMatrix() {
        matrix.translationRotateScale(
                position.x, position.y, position.z,
                rotation.x, rotation.y, rotation.z, rotation.w,
                scale.x, scale.y, scale.z);
    }

    public void updateMatrixWorld() {
        // update local matrix
        this.updateMatrix();

        // calculate matrix world
        if (parent == null) {
            matrixWorld.set(matrix);
        } else {
            parent.matrixWorld.mul(matrix, matrixWorld);
        }

        // update children
        for (Object3d child : children) {
            child.updateMatrixWorld();
        }
    }

    public Collection<Object3d> children() {
        return children;
    }

    @Nullable
    public Object3d parent() {
        return parent;
    }

    public boolean addChild(Object3d child) {
        Objects.requireNonNull(child);
        requireAcyclicHierarchy(child);

        boolean added = children.add(child);
        child.parent = this;

        return added;
    }

    public boolean removeChild(Object3d child) {
        Objects.requireNonNull(child);

        child.parent = null;

        return children.remove(child);
    }

    private void requireAcyclicHierarchy(@NotNull Object3d child) {
        Object3d parent = this.parent;

        while (parent != null) {
            if (parent == child) {
                throw new IllegalArgumentException("Cannot add parent object as child");
            }

            parent = parent.parent;
        }
    }
}

package work.lclpnet.ap2.impl.scene;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.*;

/**
 * A generic 3D-Object that can be placed into a {@link Scene}.
 * Inspired by <a href="https://github.com/mrdoob/three.js">three.js</a>.
 */
public class Object3d {

    public final Vector3d position = new Vector3d();
    public final Matrix4d matrix = new Matrix4d();
    public final Matrix4d matrixWorld = new Matrix4d();
    public final Quaterniond rotation = new Quaterniond();
    public final Vector3d scale = new Vector3d(1, 1, 1);
    private final Collection<Object3d> children = new ObjectArraySet<>();
    private Object3d parent = null;
    private int deepCount = 1;

    public void updateMatrix() {
        matrix.translationRotateScale(
                position.x, position.y, position.z,
                rotation.x, rotation.y, rotation.z, rotation.w,
                scale.x, scale.y, scale.z);
    }

    public void updateMatrixWorld() {
        updateMatrixWorld(false, true);
    }

    public void updateMatrixWorld(boolean withParent, boolean withChildren) {
        if (withParent && parent != null) {
            parent.updateMatrixWorld(true, false);
        }

        // update local matrix
        this.updateMatrix();

        // calculate matrix world
        if (parent == null) {
            matrixWorld.set(matrix);
        } else {
            parent.matrixWorld.mulAffine(matrix, matrixWorld);
        }

        if (!withChildren) return;

        // update children
        for (Object3d child : children) {
            child.updateMatrixWorld(false, true);
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

        deepCount += child.deepCount;

        return added;
    }

    public boolean removeChild(Object3d child) {
        Objects.requireNonNull(child);

        child.parent = null;

        deepCount -= child.deepCount;

        boolean removed = children.remove(child);

        for (Object3d obj : child.traverse()) {
            obj.onDetached();
        }

        this.onChildRemoved(child);

        return removed;
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

    public int deepCount() {
        return deepCount;
    }

    public Iterable<Object3d> traverse() {
        return this::traverseIterator;
    }

    private Iterator<Object3d> traverseIterator() {
        List<Object3d> queue = new LinkedList<>();
        queue.add(this);

        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return !queue.isEmpty();
            }

            @Override
            public Object3d next() {
                Object3d obj = queue.removeFirst();

                queue.addAll(obj.children());

                return obj;
            }
        };
    }

    public Vector3d worldPosition() {
        updateMatrixWorld(true, false);

        return matrixWorld.getTranslation(new Vector3d());
    }

    public Vector3d worldPosition(Vector3d local) {
        updateMatrixWorld(true, false);

        return matrixWorld.transformPosition(local);
    }

    public Vector3d localPosition(Vector3d world) {
        updateMatrixWorld(true, false);

        return new Matrix4d(matrixWorld)
                .invertAffine()
                .transformPosition(world);
    }

    /**
     * Set this object to a position in world coordinates.
     * Does not mutate the given position.
     * @param pos The position in world / global coordinates.
     */
    public void setWorldPosition(Vector3d pos) {
        position.set(pos);

        Object3d parent = parent();

        if (parent != null) {
            parent.localPosition(position);
        }
    }

    public void detach() {
        if (parent != null) {
            parent.removeChild(this);
        }
    }

    protected void onDetached() {}

    protected void onChildRemoved(Object3d child) {}
}

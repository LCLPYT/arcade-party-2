package work.lclpnet.ap2.impl.scene;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static work.lclpnet.ap2.impl.util.AssertionUtils.assertVectorsEqual;

class Object3dTest {

    private final Scene scene = new Scene(VoidMountContext.INSTANCE);
    
    @Test
    public void testAddChild() {
        Object3d object = new Object3d(scene);
        Object3d child = new Object3d(scene);

        assertTrue(object.addChild(child));
        assertEquals(1, object.children().size());
        assertEquals(object, child.parent());
    }

    @Test
    public void testRemoveChild() {
        Object3d object = new Object3d(scene);
        Object3d child = new Object3d(scene);

        object.addChild(child);
        assertTrue(object.removeChild(child));
        assertEquals(0, object.children().size());
        assertNull(child.parent());
    }

    @Test
    public void testAddChildThrowsExceptionOnCyclicHierarchy2() {
        Object3d object = new Object3d(scene);
        Object3d child = new Object3d(scene);

        object.addChild(child);

        assertThrows(IllegalArgumentException.class, () -> child.addChild(object));
    }

    @Test
    public void testAddChildThrowsExceptionOnCyclicHierarchy3() {
        Object3d object = new Object3d(scene);
        Object3d child = new Object3d(scene);
        Object3d grandChild = new Object3d(scene);

        object.addChild(child);
        child.addChild(grandChild);

        assertThrows(IllegalArgumentException.class, () -> grandChild.addChild(object));
    }

    @Test
    public void testUpdateMatrix() {
        Object3d object = new Object3d(scene);

        object.position.set(1, 2, 3);
        object.rotation.setAngleAxis(Math.PI / 2, 0, 0, 1);
        object.scale.set(2, 2, 2);

        object.updateMatrix();

        Matrix4d expectedMatrix = new Matrix4d(
                0, 2, 0, 0,
                -2, 0, 0, 0,
                0, 0, 2, 0,
                1, 2, 3, 1);

        assertTrue(expectedMatrix.equals(object.matrix, 1e-6));
    }

    @Test
    public void testUpdateMatrixWorldWithoutParent() {
        Object3d object = new Object3d(scene);

        object.position.set(1, 2, 3);
        object.rotation.set(0, 0, 0, 1);
        object.scale.set(2, 2, 2);

        object.updateMatrixWorld();

        Matrix4d expectedMatrixWorld = new Matrix4d().translationRotateScale(
                1, 2, 3,
                0, 0, 0, 1,
                2, 2, 2);

        assertEquals(expectedMatrixWorld, object.matrixWorld);
    }

    @Test
    public void testUpdateMatrixWorldWithParent() {
        Object3d object = new Object3d(scene);
        Object3d child = new Object3d(scene);

        object.position.set(1, 0, 0);
        object.rotation.set(0, 0, 0, 1);
        object.scale.set(1, 1, 1);

        child.position.set(0, 2, 0);
        child.rotation.set(0, 0, 0, 1);
        child.scale.set(1, 1, 1);

        object.addChild(child);

        object.updateMatrixWorld();

        Matrix4d expectedChildMatrixWorld = new Matrix4d().translationRotateScale(
                1, 0, 0,
                0, 0, 0, 1,
                1, 1, 1).translate(0, 2, 0);

        assertEquals(expectedChildMatrixWorld, child.matrixWorld);
    }

    @Test
    public void testAddChildWithNullThrowsException() {
        Object3d object = new Object3d(scene);

        assertThrows(NullPointerException.class, () -> object.addChild(null));
    }

    @Test
    public void testRemoveChildWithNullThrowsException() {
        Object3d object = new Object3d(scene);

        assertThrows(NullPointerException.class, () -> object.removeChild(null));
    }

    @Test
    public void testWorldTranslationInitial() {
        assertVectorsEqual(new Vector3d(), new Object3d(scene).worldTranslation());
    }

    @Test
    public void testWorldTranslationRoot() {
        Object3d obj = new Object3d(scene);
        obj.position.set(10, 20, 13);

        assertVectorsEqual(new Vector3d(10, 20, 13), obj.worldTranslation());
    }

    @Test
    public void testWorldTranslationChild() {
        Object3d obj = new Object3d(scene);
        obj.position.set(10, 20, 13);
        obj.scale.set(0.5);

        Object3d child = new Object3d(scene);
        child.position.set(4, 4, 4);

        obj.addChild(child);

        assertVectorsEqual(new Vector3d(12, 22, 15), child.worldTranslation());
    }

    @Test
    public void testWorldTranslationRelative() {
        Object3d obj = new Object3d(scene);
        obj.position.set(10, 20, 13);
        obj.scale.set(0.5);

        assertVectorsEqual(new Vector3d(11, 21, 14), obj.worldPosition(new Vector3d(2, 2, 2)));
    }

    @Test
    public void testLocalPosition() {
        Object3d obj = new Object3d(scene);
        obj.position.set(10, 20, 13);
        obj.scale.set(0.5);

        assertVectorsEqual(new Vector3d(-20, -40, -26), obj.localPosition(new Vector3d(0, 0, 0)));
    }

    @Test
    public void testLocalPositionChild() {
        Object3d obj = new Object3d(scene);
        obj.position.set(10, 20, 13);
        obj.scale.set(0.5);

        Object3d child = new Object3d(scene);
        child.position.set(4, 4, 4);

        obj.addChild(child);

        assertVectorsEqual(new Vector3d(-24, -44, -30), child.localPosition(new Vector3d(0, 0, 0)));
    }
}
package work.lclpnet.ap2.impl.util.collision;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.BlockBox;

import static org.junit.jupiter.api.Assertions.*;

class ChunkedCollisionDetectorTest {

    @Test
    void add_basic() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 2, 0, 2);
        detector.add(box);

        assertEquals(1, detector.regions.size());
        assertTrue(addedToAll(detector, box));
    }

    @Test
    void add_overlapping() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 16, 0, 2);
        detector.add(box);

        assertEquals(2, detector.regions.size());
        assertTrue(addedToAll(detector, box));
    }

    @Test
    void add_overlappingMulti() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(-1, 0, -1, 17, 0, 17);
        detector.add(box);

        assertEquals(9, detector.regions.size());
        assertTrue(addedToAll(detector, box));
    }

    @Test
    void add_unique() {
        var detector = new ChunkedCollisionDetector();

        detector.add(new BlockBox(-1, 0, -1, 17, 0, 17));
        detector.add(new BlockBox(-1, 0, -1, 17, 0, 17));

        assertTrue(detector.regions.values().stream()
                .allMatch(region -> region.colliders().size() == 1));
    }

    @Test
    void remove_basic() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 2, 0, 2);
        detector.add(box);
        detector.remove(box);

        assertEquals(1, detector.regions.size());
        assertFalse(addedToAll(detector, box));
    }

    @Test
    void remove_overlapping() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 16, 0, 2);
        detector.add(box);
        detector.remove(box);

        assertEquals(2, detector.regions.size());
        assertFalse(addedToAll(detector, box));
    }

    @Test
    void remove_overlappingMulti() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(-1, 0, -1, 17, 0, 17);
        detector.add(box);
        detector.remove(box);

        assertEquals(9, detector.regions.size());
        assertFalse(addedToAll(detector, box));
    }

    @Test
    void getCollision_basic() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 1, 0, 1);
        detector.add(box);

        assertSame(box, detector.getCollision(new Vec3d(1.5, 0, 1.5)));
    }

    @Test
    void getCollision_outside() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 1, 0, 1);
        detector.add(box);

        assertNull(detector.getCollision(new Vec3d(1.5, 1.5, 1.5)));
    }

    @Test
    void getCollision_multiRegion() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 18, 17, 2);
        detector.add(box);

        assertSame(box, detector.getCollision(new Vec3d(5, 5, 1)));
        assertSame(box, detector.getCollision(new Vec3d(17, 5, 1)));
        assertNull(detector.getCollision(new Vec3d(0, 0, 0)));
    }

    private boolean addedToAll(ChunkedCollisionDetector detector, Collider collider) {
        for (ChunkedCollisionDetector.Region region : detector.regions.values()) {
            if (!region.colliders().contains(collider)) {
                return false;
            }
        }

        return true;
    }
}
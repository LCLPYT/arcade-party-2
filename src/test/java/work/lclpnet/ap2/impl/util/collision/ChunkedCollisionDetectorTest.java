package work.lclpnet.ap2.impl.util.collision;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Set;

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

        assertEquals(Set.of(box), detector.getCollisions(new Vec3d(1.5, 0, 1.5)));
    }

    @Test
    void getCollision_outside() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 1, 0, 1);
        detector.add(box);

        assertEquals(Set.of(), detector.getCollisions(new Vec3d(1.5, 1.5, 1.5)));
    }

    @Test
    void getCollision_multiRegion() {
        var detector = new ChunkedCollisionDetector();

        BlockBox box = new BlockBox(1, 0, 1, 18, 17, 2);
        detector.add(box);

        assertEquals(Set.of(box), detector.getCollisions(new Vec3d(5, 5, 1)));
        assertEquals(Set.of(box), detector.getCollisions(new Vec3d(17, 5, 1)));
        assertEquals(Set.of(), detector.getCollisions(new Vec3d(0, 0, 0)));
    }

    @Test
    void getCollisions_multiple() {
        var detector = new ChunkedCollisionDetector();

        BlockBox fst = new BlockBox(0, 0, 0, 7, 7, 7);
        BlockBox snd = new BlockBox(4, 4, 4, 6, 6, 6);
        BlockBox trd = new BlockBox(6, 6, 6, 10, 10, 10);

        detector.add(fst);
        detector.add(snd);
        detector.add(trd);

        assertEquals(Set.of(fst), detector.getCollisions(new Vec3d(1, 1, 1)));
        assertEquals(Set.of(fst, snd), detector.getCollisions(new Vec3d(5, 5, 5)));
        assertEquals(Set.of(fst, snd, trd), detector.getCollisions(new Vec3d(6, 6, 6)));
        assertEquals(Set.of(fst, trd), detector.getCollisions(new Vec3d(7, 7, 7)));
        assertEquals(Set.of(trd), detector.getCollisions(new Vec3d(8, 8, 8)));
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
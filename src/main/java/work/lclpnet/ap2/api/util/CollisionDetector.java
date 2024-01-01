package work.lclpnet.ap2.api.util;

import net.minecraft.util.math.Position;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.collision.CollisionInfo;

import java.util.HashSet;
import java.util.Set;

public interface CollisionDetector {

    void add(Collider collider);

    void remove(Collider collider);

    /**
     * Finds all colliders that collide with a point.
     * @param pos The position to check collisions with.
     * @param info The {@link CollisionInfo} object that collisions will be written to.
     */
    void updateCollisions(Position pos, CollisionInfo info);

    @NotNull
    default Set<Collider> getCollisions(Position pos) {
        CollisionInfo info = new CollisionInfo(1);
        updateCollisions(pos, info);

        Set<Collider> collisions = new HashSet<>(info.count());

        for (Collider collider : info) {
            collisions.add(collider);
        }

        return collisions;
    }
}

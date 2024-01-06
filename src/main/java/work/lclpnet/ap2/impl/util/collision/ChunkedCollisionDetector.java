package work.lclpnet.ap2.impl.util.collision;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Position;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.impl.util.math.Vec2i;

import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public class ChunkedCollisionDetector implements CollisionDetector {

    protected final Long2ObjectMap<Region> regions = new Long2ObjectArrayMap<>();

    @Override
    public void add(Collider collider) {
        Objects.requireNonNull(collider);

        for (Vec2i reg : iterateRegions(collider)) {
            var region = regions.computeIfAbsent(hashRegion(reg.x(), reg.z()), l -> Region.create());

            region.colliders.add(collider);
        }
    }

    @Override
    public void remove(Collider collider) {
        if (collider == null) return;

        for (Vec2i reg : iterateRegions(collider)) {
            var region = regions.computeIfAbsent(hashRegion(reg.x(), reg.z()), l -> Region.create());

            region.colliders.remove(collider);
        }
    }

    @Override
    public void updateCollisions(Position pos, CollisionInfo info) {
        info.reset();

        Region region = regions.get(hashPos(pos.getX(), pos.getZ()));

        if (region == null) return;

        region.updateCollisions(pos, info);
    }

    private static long hashPos(double x, double z) {
        return hashRegion(
                ChunkSectionPos.getSectionCoord(x),
                ChunkSectionPos.getSectionCoord(z)
        );
    }

    private static long hashRegion(int rx, int rz) {
        return ChunkPos.toLong(rx, rz);
    }

    private static Iterable<Vec2i> iterateRegions(Collider collider) {
        BlockPos min = collider.getMin(), max = collider.getMax();

        int minRx = ChunkSectionPos.getSectionCoord(min.getX()), minRz = ChunkSectionPos.getSectionCoord(min.getZ());
        int maxRx = ChunkSectionPos.getSectionCoord(max.getX()), maxRz = ChunkSectionPos.getSectionCoord(max.getZ());

        Vec2i.Mutable pos = new Vec2i.Mutable(minRx, minRz);

        return () -> new Iterator<>() {
            private int rx = minRx, rz = minRz;

            @Override
            public boolean hasNext() {
                return rx <= maxRx && rz <= maxRz;
            }

            @Override
            public Vec2i next() {
                pos.set(rx, rz);

                rz++;

                if (rz > maxRz) {
                    rx++;
                    rz = minRz;
                }

                return pos;
            }
        };
    }

    protected record Region(Set<Collider> colliders) {

        public void updateCollisions(Position pos, CollisionInfo info) {
            for (Collider collider : colliders) {
                if (collider.collidesWith(pos)) {
                    info.add(collider);
                }
            }
        }

        static Region create() {
            return new Region(new ObjectArraySet<>(4));
        }
    }
}

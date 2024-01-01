package work.lclpnet.ap2.impl.util.collision;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import work.lclpnet.ap2.api.util.Collider;

public class UnionCollider implements Collider {

    private final Collider[] children;
    private final BlockPos min, max;

    public UnionCollider(Collider[] children) {
        if (children.length == 0) throw new IllegalStateException("Children must not be empty");

        this.children = children;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Collider child : children) {
            BlockPos min = child.getMin();
            BlockPos max = child.getMax();

            minX = Math.min(minX, min.getX());
            minY = Math.min(minY, min.getY());
            minZ = Math.min(minZ, min.getZ());
            maxX = Math.max(maxX, max.getX());
            maxY = Math.max(maxY, max.getY());
            maxZ = Math.max(maxZ, max.getZ());
        }

        this.min = new BlockPos(minX, minY, minZ);
        this.max = new BlockPos(maxX, maxY, maxZ);
    }

    @Override
    public boolean collidesWith(Position pos) {
        for (Collider child : children) {
            if (child.collidesWith(pos)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public BlockPos getMin() {
        return min;
    }

    @Override
    public BlockPos getMax() {
        return max;
    }

    public static UnionCollider union(Collider... colliders) {
        return new UnionCollider(colliders);
    }
}

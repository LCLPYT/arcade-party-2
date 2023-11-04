package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.util.Collider;

import java.util.Iterator;
import java.util.Objects;

public class BlockBox implements Pair<BlockPos, BlockPos>, Iterable<BlockPos>, Collider {

    private final BlockPos min, max;

    public BlockBox(BlockPos first, BlockPos second) {
        this(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
    }

    public BlockBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.min = new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
        this.max = new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    @Override
    public BlockPos left() {
        return min;
    }

    @Override
    public BlockPos right() {
        return max;
    }

    @NotNull
    @Override
    public Iterator<BlockPos> iterator() {
        return BlockPos.iterate(min, max).iterator();
    }

    @Override
    public boolean collidesWith(Position pos) {
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();

        return x >= min.getX() && x < max.getX() + 1
               && y >= min.getY() && y < max.getY() + 1
               && z >= min.getZ() && z < max.getZ() + 1;
    }

    @Override
    public BlockPos getMin() {
        return min;
    }

    @Override
    public BlockPos getMax() {
        return max;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockBox blockPos = (BlockBox) o;
        return Objects.equals(min, blockPos.min) && Objects.equals(max, blockPos.max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    public Vec3d getCenter() {
        return new Vec3d(
                (min.getX() + max.getX()) * 0.5d,
                (min.getY() + max.getY()) * 0.5d,
                (min.getZ() + max.getZ()) * 0.5d
        );
    }
}

package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.math.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.Collider;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;

import java.util.Iterator;
import java.util.Objects;
import java.util.Random;

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
    public boolean collidesWith(double x, double y, double z) {
        return contains(x, y, z);
    }

    @Override
    public BlockPos getMin() {
        return min;
    }

    @Override
    public BlockPos getMax() {
        return max;
    }

    public int getWidth() {
        return max.getX() - min.getX();
    }

    public int getHeight() {
        return max.getY() - min.getY();
    }

    public int getLength() {
        return max.getZ() - min.getZ();
    }

    public BlockBox transform(AffineIntMatrix mat4) {
        return new BlockBox(mat4.transform(min), mat4.transform(max));
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

    @Override
    public String toString() {
        return "BlockBox{" +
               "min=" + min +
               ", max=" + max +
               '}';
    }

    @Nullable
    public Direction getTangentSurface(int x, int y, int z) {
        if (x == min.getX()) return Direction.WEST;
        if (x == max.getX()) return Direction.EAST;
        if (z == min.getZ()) return Direction.NORTH;
        if (z == max.getZ()) return Direction.SOUTH;
        if (y == min.getY()) return Direction.DOWN;
        if (y == max.getY()) return Direction.UP;

        return null;
    }

    public Vec3d getCenter() {
        return new Vec3d(
                (min.getX() + max.getX()) * 0.5d,
                (min.getY() + max.getY()) * 0.5d,
                (min.getZ() + max.getZ()) * 0.5d
        );
    }

    public boolean contains(double x, double y, double z) {
        return x >= min.getX() && x < max.getX() + 1
               && y >= min.getY() && y < max.getY() + 1
               && z >= min.getZ() && z < max.getZ() + 1;
    }

    public boolean contains(Position pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean contains(Box box) {
        return contains(box.minX, box.minY, box.minZ) && contains(box.maxX, box.maxY, box.maxZ);
    }

    public boolean collidesWith(BlockBox other) {
        return this.max.getX() >= other.min.getX() && other.max.getX() >= this.min.getX()
               && this.max.getY() >= other.min.getY() && other.max.getY() >= this.min.getY()
               && this.max.getZ() >= other.min.getZ() && other.max.getZ() >= this.min.getZ();
    }

    public void getRandomBlockPos(BlockPos.Mutable pos, Random random) {
        int minX = min.getX(), minY = min.getY(), minZ = min.getZ();
        int maxX = max.getX(), maxY = max.getY(), maxZ = max.getZ();

        int x = minX + random.nextInt(maxX - minX + 1);
        int y = minY + random.nextInt(maxY - minY + 1);
        int z = minZ + random.nextInt(maxZ - minZ + 1);

        pos.set(x, y, z);
    }

    public Vec3d getRandomPos(Random random) {
        int minX = min.getX(), minY = min.getY(), minZ = min.getZ();
        int maxX = max.getX(), maxY = max.getY(), maxZ = max.getZ();

        double x = minX + random.nextDouble(maxX - minX + 1);
        double y = minY + random.nextDouble(maxY - minY + 1);
        double z = minZ + random.nextDouble(maxZ - minZ + 1);

        return new Vec3d(x, y, z);
    }

    public double squaredDistanceTo(Vec3d pos) {
        return getClosestPoint(pos).squaredDistanceTo(pos);
    }

    public Vec3d getClosestPoint(Position pos) {
        return getClosestPoint(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vec3d getClosestPoint(double x, double y, double z) {
        return new Vec3d(
                Math.max(Math.min(x, max.getX()), min.getX()),
                Math.max(Math.min(y, max.getY()), min.getY()),
                Math.max(Math.min(z, max.getZ()), min.getZ())
        );
    }
}

package work.lclpnet.ap2.impl.util.world.block_shape;

import com.google.common.collect.Iterators;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.gaco.ds.BlockBox;

import java.util.Iterator;
import java.util.Optional;

public class CylinderBlockShape implements BlockShape, BlockShape.WithRadius, BlockShape.WithHeight {

    public static final String TYPE = "cylinder", TYPE_CIRCLE = "circle";

    public static final MapCodec<CylinderBlockShape> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(CylinderBlockShape::origin),
            ExtraCodecs.POSITIVE_INT.fieldOf("radius").forGetter(CylinderBlockShape::radius),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("height").forGetter(shape -> Optional.of(shape.height()))
    ).apply(instance, (pos, radius, height) -> new CylinderBlockShape(pos, radius, height.orElse(1))));

    private final BlockPos origin;
    private final int radius;
    private final int radiusSq;
    private final int height;
    private final BlockBox bounds;
    private final BlockPos center;

    public CylinderBlockShape(BlockPos origin, int radius, int height) {
        if (radius <= 0) throw new IllegalArgumentException("Radius must be positive");
        if (height <= 0) throw new IllegalArgumentException("Height must be positive");

        this.origin = origin;
        this.radius = radius;
        this.radiusSq = radius * radius;
        this.height = height;
        this.bounds = new BlockBox(origin.offset(-radius, 0, -radius), origin.offset(radius, height - 1, radius));
        this.center = origin.offset(0, height / 2, 0);
    }

    @Override
    public BlockShapes.Type<?> type() {
        return height == 1 ? BlockShapes.TYPE_CIRCLE : BlockShapes.TYPE_CYLINDER;
    }

    @Override
    public BlockPos origin() {
        return origin;
    }

    @Override
    public BlockPos center() {
        return center;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        int oy = origin.getY();

        if (y < oy || y >= oy + height) return false;

        double dx = x - (origin.getX() + 0.5);
        double dz = z - (origin.getZ() + 0.5);

        return dx * dx + dz * dz < radiusSq;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return contains(x + 0.5, y, z + 0.5);
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    @Override
    public @NotNull Iterator<BlockPos> iterator() {
        return Iterators.filter(bounds.iterator(), this::contains);
    }

    @Override
    public int radius() {
        return radius;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public boolean collidesWith(AABB box) {
        if (!bounds.collidesWith(box)) {
            return false;
        }

        for (BlockPos pos : BlockPos.betweenClosed(box)) {
            if (contains(pos)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public @NotNull Vec3 project(@NotNull Position pos) {
        double y = Math.clamp(pos.y(), origin.getY(), origin.getY() + height);

        double ox = origin.getX() + 0.5;
        double oz = origin.getZ() + 0.5;

        double dx = pos.x() - ox;
        double dz = pos.z() - oz;

        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist > radius) {
            dx = dx / dist * radius;
            dz = dz / dist * radius;
        }

        return new Vec3(ox + dx, y, oz + dz);
    }
}

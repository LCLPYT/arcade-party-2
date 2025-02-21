package work.lclpnet.ap2.impl.util.world.stage;

import com.google.common.collect.Iterators;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Iterator;

public class CylinderBlockShape implements BlockShape, BlockShape.WithRadius, BlockShape.WithHeight {

    public static final String TYPE = "cylinder";
    public static final String TYPE_CIRCLE = "circle";
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
        this.bounds = new BlockBox(origin.add(-radius, 0, -radius), origin.add(radius, height - 1, radius));
        this.center = origin.add(0, height / 2, 0);
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
    public boolean contains(BlockPos pos) {
        int y = pos.getY();
        int oy = origin.getY();

        if (y < oy || y > oy + height - 1) return false;

        float ox = origin.getX() + 0.5f, oz = origin.getZ() + 0.5f;
        float x = pos.getX() + 0.5f, z = pos.getZ() + 0.5f;

        float dx = x - ox, dz = z - oz;

        return dx * dx + dz * dz < radiusSq;
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
}

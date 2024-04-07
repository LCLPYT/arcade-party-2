package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.FilterIterator;

import java.util.Iterator;

public class CylinderStage implements Stage {

    public static final String TYPE = "cylinder";
    private final BlockPos origin;
    private final int radiusSq;
    private final int height;
    private final BlockBox groundBounds;

    public CylinderStage(BlockPos origin, int radius, int height) {
        if (radius <= 0) throw new IllegalArgumentException("Radius must be positive");
        if (height <= 0) throw new IllegalArgumentException("Height must be positive");

        this.origin = origin;
        this.radiusSq = radius * radius;
        this.height = height;
        this.groundBounds = new BlockBox(origin.add(-radius, 0, -radius), origin.add(radius, 0, radius));
    }

    @Override
    public BlockPos getOrigin() {
        return origin;
    }

    @Override
    public Iterator<BlockPos> iterateGroundPositions() {
        return new FilterIterator<>(groundBounds.iterator(), this::contains);
    }

    private boolean contains(BlockPos pos) {
        int y = pos.getY();
        int oy = origin.getY();

        if (y < oy || y > oy + height - 1) return false;

        float ox = origin.getX() + 0.5f, oz = origin.getZ() + 0.5f;
        float x = pos.getX() + 0.5f, z = pos.getZ() + 0.5f;

        float dx = x - ox, dz = z - oz;

        return dx * dx + dz * dz < radiusSq;
    }
}

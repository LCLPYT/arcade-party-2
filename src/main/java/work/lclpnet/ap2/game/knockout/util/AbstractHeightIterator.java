package work.lclpnet.ap2.game.knockout.util;

import net.minecraft.util.math.BlockPos;

import java.util.Iterator;

public abstract class AbstractHeightIterator implements Iterator<BlockPos> {

    private boolean hasNext = false, done = false, has2d = false;
    private final BlockPos.Mutable current = new BlockPos.Mutable();
    protected final int radius;
    protected final int centerX, centerZ;
    protected final int minY, maxY;
    protected int rx = 0, rz = 0;
    private int y;

    public AbstractHeightIterator(int radius, int centerX, int centerZ, int minY, int maxY) {
        this.radius = radius;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.minY = minY;
        this.maxY = maxY;
        this.y = minY;
    }

    @Override
    public boolean hasNext() {
        if (!hasNext) {
            advance();
        }

        return !done;
    }

    @Override
    public BlockPos next() {
        hasNext = false;
        return current;
    }

    private void advance() {
        if (!has2d) {
            if (!advance2d()) {
                done = true;
                hasNext = true;  // do not advance again
                return;
            }

            has2d = true;
        }

        // iterate all y positions of current x and z

        if (y > maxY) {
            // go to next x and z
            has2d = false;
            y = minY;

            advance();

            return;
        }

        current.set(centerX + rx - radius, y++, centerZ + rz - radius);
        hasNext = true;
    }

    protected abstract boolean advance2d();
}

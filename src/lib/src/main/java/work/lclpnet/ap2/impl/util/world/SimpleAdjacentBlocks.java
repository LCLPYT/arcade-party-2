package work.lclpnet.ap2.impl.util.world;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.world.BlockPredicate;
import work.lclpnet.ap2.util.world.AdjacentBlocks;

import java.util.Iterator;

import static java.lang.Math.max;

/**
 * An adjacent block iterator that provides neighbours in the four horizontal directions.
 * Steps of one block up or down are also supported.
 */
public class SimpleAdjacentBlocks implements AdjacentBlocks {

    private final BlockPredicate predicate;
    private final int stepUp, stepDown;

    public SimpleAdjacentBlocks(BlockPredicate predicate, int verticalStep) {
        this(predicate, verticalStep, verticalStep);
    }

    public SimpleAdjacentBlocks(BlockPredicate predicate, int stepUp, int stepDown) {
        this.predicate = predicate;
        this.stepUp = stepUp;
        this.stepDown = stepDown;
    }

    @Override
    public Iterator<BlockPos> getAdjacent(BlockPos pos) {
        return iterator(pos.getX(), pos.getY(), pos.getZ());
    }

    @NotNull
    private Iterator<BlockPos> iterator(int x, int y, int z) {
        return new Iterator<>() {
            boolean done = false, hasNext = false;
            int i = 0;
            final BlockPos.MutableBlockPos current = new BlockPos.MutableBlockPos();

            @Override
            public boolean hasNext() {
                if (!hasNext) advance();

                return !done;
            }

            @Override
            public BlockPos next() {
                hasNext = false;
                return current;
            }

            private void advance() {
                switch (i++) {
                    case 0 -> {
                        current.set(x + 1, y, z);
                        if (invalid()) advance();
                        else hasNext = true;
                    }
                    case 1 -> {
                        current.set(x, y, z + 1);
                        if (invalid()) advance();
                        else hasNext = true;
                    }
                    case 2 -> {
                        current.set(x - 1, y, z);
                        if (invalid()) advance();
                        else hasNext = true;
                    }
                    case 3 -> {
                        current.set(x, y, z - 1);
                        if (invalid()) advance();
                        else hasNext = true;
                    }
                    default -> {
                        done = true;
                        hasNext = true;
                    }
                }
            }

            private boolean invalid() {
                if (predicate.test(current)) return false;

                int verticalStep = max(stepUp, stepDown);

                for (int j = 1; j <= verticalStep; j++) {
                    if (j <= stepUp) {
                        current.setY(y + j);
                        if (predicate.test(current)) return false;
                    }

                    if (j <= stepDown) {
                        current.setY(y - j);
                        if (predicate.test(current)) return false;
                    }
                }

                return true;
            }
        };
    }

    @Nullable
    private BlockPos validate(BlockPos adj) {
        if (predicate.test(adj)) return adj;

        BlockPos offset = adj.above();
        if (predicate.test(offset)) return offset;

        offset = adj.below();
        if (predicate.test(offset)) return offset;

        return null;
    }
}

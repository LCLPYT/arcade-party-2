package work.lclpnet.ap2.impl.util.world;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.api.util.world.BlockPredicate;

import java.util.Iterator;

/**
 * An adjacent block iterator that provides neighbours in the four horizontal directions.
 * Steps of one block up or down are also supported.
 */
public class SimpleAdjacentBlocks implements AdjacentBlocks {

    private final BlockPredicate predicate;

    public SimpleAdjacentBlocks(BlockPredicate predicate) {
        this.predicate = predicate;
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
            final BlockPos.Mutable current = new BlockPos.Mutable();

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

                current.setY(y + 1);
                if (predicate.test(current)) return false;

                current.setY(y - 1);
                return !predicate.test(current);
            }
        };
    }

    @Nullable
    private BlockPos validate(BlockPos adj) {
        if (predicate.test(adj)) return adj;

        BlockPos offset = adj.up();
        if (predicate.test(offset)) return offset;

        offset = adj.down();
        if (predicate.test(offset)) return offset;

        return null;
    }
}

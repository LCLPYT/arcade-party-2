package work.lclpnet.ap2.impl.util.world;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;
import work.lclpnet.ap2.api.util.world.WorldScanner;

import java.util.*;

public class BfsWorldScanner implements WorldScanner {

    private final AdjacentBlocks adjacentBlocks;

    public BfsWorldScanner(AdjacentBlocks adjacentBlocks) {
        this.adjacentBlocks = adjacentBlocks;
    }

    @Override
    public Iterator<BlockPos> scan(BlockPos start) {
        final List<BlockPos> queue = new ArrayList<>();
        final Set<BlockPos> known = new HashSet<>();

        queue.add(start);
        known.add(start);

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !queue.isEmpty();
            }

            @Override
            public BlockPos next() {
                BlockPos current = queue.removeFirst();

                advance(current);

                return current;
            }

            private void advance(BlockPos pos) {
                for (BlockPos adj : adjacentBlocks.iterate(pos)) {
                    if (known.contains(adj)) continue;

                    BlockPos immutable = adj.toImmutable();
                    queue.add(immutable);
                    known.add(immutable);
                }
            }
        };
    }
}

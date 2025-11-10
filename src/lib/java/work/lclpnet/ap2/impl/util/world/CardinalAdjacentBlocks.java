package work.lclpnet.ap2.impl.util.world;

import com.google.common.collect.AbstractIterator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import work.lclpnet.ap2.api.util.world.AdjacentBlocks;

import java.util.Iterator;
import java.util.function.Predicate;

public class CardinalAdjacentBlocks implements AdjacentBlocks {

    private final Predicate<BlockPos> predicate;

    public CardinalAdjacentBlocks(Predicate<BlockPos> predicate) {
        this.predicate = predicate;
    }

    @Override
    public Iterator<BlockPos> getAdjacent(BlockPos pos) {
        var mut = new BlockPos.Mutable();

        return new AbstractIterator<>() {
            int i = 0;

            @Override
            protected BlockPos computeNext() {
                while (i < 6) {
                    mut.set(pos, Direction.values()[i++]);

                    if (predicate.test(mut)) {
                        return mut;
                    }
                }

                endOfData();

                return null;
            }
        };
    }
}

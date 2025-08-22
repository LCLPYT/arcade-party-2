package work.lclpnet.ap2.game.splashy_dropper.data;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public record SdShape(BlockPos[] positions, BlockPos[] adjacent) {

    public static final int[] ADJ_OFFSETS = new int[]{
            +1, +0,
            +1, +1,
            +0, +1,
            -1, +0,
            -1, -1,
            +0, -1,
            +1, -1,
            -1, +1
    };

    public SdShape(BlockPos[] positions) {
        this(positions, computeAdjacent(positions));
    }

    public boolean hasSpace(Set<BlockPos> space, BlockPos pos) {
        for (BlockPos shapePos : positions) {
            if (!space.contains(pos.add(shapePos))) {
                return false;
            }
        }

        return true;
    }

    public Iterator<BlockPos> combinedPositions() {
        final int len = positions.length + adjacent.length;

        return new Iterator<>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < len;
            }

            @Override
            public BlockPos next() {
                if (i < positions.length) {
                    return positions[i++];
                }

                return adjacent[i++ - positions.length];
            }
        };
    }

    public static SdShape square(int width, int length) {
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("Square dimensions must be positive");
        }

        var positions = BlockPos.stream(0, 0, 0, width - 1, 0, length - 1)
                .map(BlockPos::toImmutable)
                .toArray(BlockPos[]::new);

        return new SdShape(positions);
    }

    private static BlockPos[] computeAdjacent(BlockPos[] positions) {
        Set<BlockPos> set = new HashSet<>();

        for (BlockPos pos : positions) {
            for (int i = 0; i < ADJ_OFFSETS.length; i += 2) {
                set.add(pos.add(ADJ_OFFSETS[i], 0, ADJ_OFFSETS[i + 1]));
            }
        }

        for (BlockPos pos : positions) {
            set.remove(pos);
        }

        return set.toArray(BlockPos[]::new);
    }
}

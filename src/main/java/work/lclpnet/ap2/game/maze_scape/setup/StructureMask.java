package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.util.math.Direction;
import work.lclpnet.ap2.game.maze_scape.util.GreedyMeshing;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.Arrays;

public record StructureMask(boolean[][][] mask, int width, int height, int length) implements GreedyMeshing.VoxelView {

    public StructureMask(int width, int height, int length) {
        this(fill(new boolean[width][height][length], false), width, height, length);
    }

    @Override
    public boolean isVoxelAt(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length && mask[x][y][z];
    }

    public boolean isBorder(int x, int y, int z) {
        if (x <= 0 || x >= width - 1 || y <= 0 || y >= height - 1 || z <= 0 || z >= length - 1) {
            return true;
        }

        for (Direction dir : Direction.values()) {
            if (!isVoxelAt(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ())) {
                return true;
            }
        }

        return false;
    }

    public GreedyMeshing greedyMeshing() {
        return new GreedyMeshing(width, height, length, this);
    }

    public static StructureMask nonAir(BlockStructure structure) {
        // init new empty mask
        int width = structure.getWidth(), height = structure.getHeight(), length = structure.getLength();
        boolean[][][] mask = fill(new boolean[width][height][length], false);

        var origin = structure.getOrigin();

        for (KibuBlockPos pos : structure.getBlockPositions()) {
            if (structure.getBlockState(pos).isAir()) continue;

            mask[pos.getX() - origin.getX()][pos.getY() - origin.getY()][pos.getZ() - origin.getZ()] = true;
        }

        return new StructureMask(mask, width, height, length);
    }

    public static boolean[][][] fill(boolean[][][] mask, boolean val) {
        for (boolean[][] slices : mask) {
            for (boolean[] slice : slices) {
                Arrays.fill(slice, val);
            }
        }

        return mask;
    }
}

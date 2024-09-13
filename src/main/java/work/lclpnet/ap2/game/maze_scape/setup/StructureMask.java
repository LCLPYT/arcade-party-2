package work.lclpnet.ap2.game.maze_scape.setup;

import work.lclpnet.ap2.game.maze_scape.util.GreedyMeshing;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.structure.BlockStructure;

import java.util.Arrays;

public record StructureMask(boolean[][][] mask, int width, int height, int length) implements GreedyMeshing.VoxelView {

    @Override
    public boolean isVoxelAt(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length && mask[x][y][z];
    }

    public static StructureMask nonAir(BlockStructure structure) {
        // init new empty mask
        int width = structure.getWidth(), height = structure.getHeight(), length = structure.getLength();
        boolean[][][] mask = fill(new boolean[width][height][length], width, height, false);

        var origin = structure.getOrigin();

        for (KibuBlockPos pos : structure.getBlockPositions()) {
            if (structure.getBlockState(pos).isAir()) continue;

            mask[pos.getX() - origin.getX()][pos.getY() - origin.getY()][pos.getZ() - origin.getZ()] = true;
        }

        return new StructureMask(mask, width, height, length);
    }

    public static boolean[][][] fill(boolean[][][] mask, int width, int height, boolean val) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Arrays.fill(mask[x][y], val);
            }
        }

        return mask;
    }
}

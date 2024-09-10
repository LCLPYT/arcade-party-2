package work.lclpnet.ap2.game.maze_scape.gen.test;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.game.maze_scape.util.GreedyMeshing;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class String2dVoxelView implements GreedyMeshing.VoxelView {

    private final String[] lines;
    private final int width, height;

    public String2dVoxelView(String string, int width, int height) {
        this.lines = string.split("\n");
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean isVoxelAt(int x, int y, int z) {
        if (z != 0 || x < 0 || x >= width || y < 0 || y >= height) return false;

        return lines[y].charAt(x) != '0';
    }

    public static @NotNull String genString(int height, int width, List<BlockBox> boxes) {
        char[][] chars = new char[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                chars[y][x] = '0';
            }
        }

        char c = 'a';

        for (BlockBox box : boxes) {
            for (BlockPos pos : box) {
                chars[pos.getY()][pos.getX()] = c;
            }
            c++;
        }

        return Arrays.stream(chars)
                .map(String::new)
                .collect(Collectors.joining("\n"));
    }
}

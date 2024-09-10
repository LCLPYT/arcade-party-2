package work.lclpnet.ap2.game.maze_scape.gen.test;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.game.maze_scape.util.GreedyMeshing;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class String3dVoxelView implements GreedyMeshing.VoxelView {

    private final String[][] lines;
    private final int width, height, length;

    public String3dVoxelView(String[] strings, int width, int height, int length) {
        String[][] lines = new String[strings.length][];

        for (int i = 0; i < strings.length; i++) {
            lines[i] = strings[i].split("\n");
        }

        this.lines = lines;
        this.width = width;
        this.height = height;
        this.length = length;
    }

    @Override
    public boolean isVoxelAt(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) return false;

        char c = lines[y][z].charAt(x);
        return c != '0';
    }

    public static String[] genStrings(int height, int width, int length, List<BlockBox> boxes) {
        char[][][] chars = new char[height][length][width];

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    chars[y][z][x] = '0';
                }
            }
        }

        char c = 'a';

        for (BlockBox box : boxes) {
            for (BlockPos pos : box) {
                chars[pos.getY()][pos.getZ()][pos.getX()] = c;
            }
            c++;
        }

        return Arrays.stream(chars)
                .map(Arrays::stream)
                .map(stream -> stream.map(String::new)
                        .collect(Collectors.joining("\n")))
                .toArray(String[]::new);
    }
}

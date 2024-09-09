package work.lclpnet.ap2.game.maze_scape.gen.test;

import work.lclpnet.ap2.game.maze_scape.util.GreedyMeshing;

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

        return lines[y].charAt(x) != ' ';
    }
}

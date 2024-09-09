package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GreedyMeshing {

    private final int width;
    private final int height;
    private final int length;
    private final VoxelView voxelView;
    private final boolean[][][] visited;

    public GreedyMeshing(int width, int height, int length, VoxelView voxelView) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.voxelView = voxelView;
        this.visited = new boolean[width][height][length];

        // make sure every boolean element is false
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Arrays.fill(this.visited[x][y], false);
            }
        }
    }

    public List<BlockBox> generateBoxes() {
        List<BlockBox> meshes = new ArrayList<>();

        for (int z = 0; z < length; z++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (!voxelView.isVoxelAt(x, y, z) || visited[x][y][z]) continue;

                    BlockBox box = findMaxBox(x, y, z);
                    markVisited(box);
                    meshes.add(box);
                }
            }
        }

        return meshes;
    }

    private BlockBox findMaxBox(int startX, int startY, int startZ) {
        int maxX = startX, maxY = startY, maxZ = startZ;

        // expand x
        while (maxX + 1 < width && voxelView.isVoxelAt(maxX + 1, startY, startZ) && !visited[maxX + 1][startY][startZ]) {
            maxX++;
        }

        // expand y
        boolean canExpandY = true;

        while (canExpandY && maxY + 1 < height) {
            for (int x = startX; x <= maxX; x++) {
                if (voxelView.isVoxelAt(x, maxY + 1, startZ) && !visited[x][maxY + 1][startZ]) continue;

                canExpandY = false;
                break;
            }

            if (canExpandY) maxY++;
        }

        // expand z
        boolean canExpandZ = true;

        while (canExpandZ && maxZ + 1 < length) {
            for (int x = startX; x <= maxX; x++) {
                for (int y = startY; y <= maxY; y++) {
                    if (voxelView.isVoxelAt(x, y, maxZ + 1) && !visited[x][y][maxZ + 1]) continue;

                    canExpandZ = false;
                    break;
                }

                if (!canExpandZ) break;
            }

            if (canExpandZ) maxZ++;
        }

        return new BlockBox(startX, startY, startZ, maxX, maxY, maxZ);
    }

    private void markVisited(BlockBox box) {
        BlockPos min = box.min(), max = box.max();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    visited[x][y][z] = true;
                }
            }
        }
    }

    public interface VoxelView {
        
        boolean isVoxelAt(int x, int y, int z);
    }
}

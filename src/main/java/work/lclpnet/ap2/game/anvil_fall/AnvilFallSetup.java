package work.lclpnet.ap2.game.anvil_fall;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Random;

public class AnvilFallSetup {

    private final int startX, startY, startZ;
    private final int width, length;
    private final byte[] height;
    private final Random random;

    public AnvilFallSetup(int startX, int startY, int startZ, int width, int length, byte[] height, Random random) {
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.width = width;
        this.length = length;
        this.height = height;
        this.random = random;
    }

    private int getIndexX(int x) {
        return x - startX;
    }

    private int getIndexZ(int z) {
        return z - startZ;
    }

    private int getIndexFromIndices(int ix, int iz) {
        return iz * width + ix;
    }

    private int getIndex(int x, int z) {
        int ix = getIndexX(x);
        int iz = getIndexZ(z);

        return getIndexFromIndices(ix, iz);
    }

    /**
     * Get a random position that is within a given square radius with a probability of ~68.2%.
     * @param targetX The mean x.
     * @param targetZ The mean z.
     * @param range The range (standard derivation).
     * @return A normal-distributed random position.
     */
    public BlockPos getRandomPosition(int targetX, int targetZ, double range) {
        // now sample from a normal distribution biased towards the player x and z
        int x = (int) Math.round(random.nextGaussian(targetX, range));
        int z = (int) Math.round(random.nextGaussian(targetZ, range));

        if (x < startX || x >= startX + width || z < startZ || z >= startZ + length) {
            return getRandomPosition();
        }

        int i = getIndex(x, z);
        int y = getRandomY(i);

        return new BlockPos(x, y, z);
    }

    public BlockPos getRandomPosition() {
        int i, j = 0;

        // try to find an index that is not empty
        do {
            i = random.nextInt(height.length);
        } while (height[i] <= 0 && j++ < 16);

        // re-construct xyz
        int x, y, z;

        if (height[i] <= 0) {
            // index is empty, just assume the center xz and startY
            x = (2 * startX + width - 1) / 2;
            y = startY;
            z = (2 * startZ + length - 1) / 2;
        } else {
            x = startX + i % width;
            y = getRandomY(i);
            z = startZ + i / width;
        }

        return new BlockPos(x, y, z);
    }

    public BlockPos getRandomPositionAt(int x, int z) {
        int i = getIndex(x, z);

        if (i < 0 || i >= height.length || height[i] <= 0) {
            return getRandomPosition(x, z, 2.0);
        }

        int y = getRandomY(i);

        return new BlockPos(x, y, z);
    }

    private int getRandomY(int i) {
        return startY + random.nextInt(height[i]);
    }

    public static AnvilFallSetup scanWorld(BlockView world, BlockBox box, Random random) {
        BlockPos min = box.getMin(), max = box.getMax();

        final int xMin = min.getX(), yMin = min.getY(), zMin = min.getZ();
        final int xMax = max.getX(), yMax = max.getY(), zMax = max.getZ();
        final int width = xMax - xMin + 1, length = zMax - zMin + 1;

        BlockPos.Mutable pos = new BlockPos.Mutable();

        byte[] heights = new byte[width * length];

        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                byte height = 0;

                // count how many blocks are supported above
                for (int y = yMin; y <= yMax && height < Byte.MAX_VALUE; y++) {
                    pos.set(x, y, z);

                    BlockState state = world.getBlockState(pos);
                    if (!state.getCollisionShape(world, pos).isEmpty()) break;

                    height++;
                }

                if (height < 0) height = 0;

                int ix = x - xMin, iz = z - zMin;

                heights[iz * width + ix] = height;
            }
        }

        return new AnvilFallSetup(xMin, yMin, zMin, width, length, heights, random);
    }
}

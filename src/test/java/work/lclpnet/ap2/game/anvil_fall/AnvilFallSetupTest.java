package work.lclpnet.ap2.game.anvil_fall;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AnvilFallSetupTest {

    @Test
    void getRandomPosition() {
        Random random = new Random(12345);

        final int width = 10, height = 10;
        final int startX = 5, startZ = 5;

        byte[] heights = new byte[width * height];
        Arrays.fill(heights, (byte) 1);

        var setup = new AnvilFallSetup(startX, 0, startZ, width, height, heights, random);

        int[] counts = new int[heights.length];
        Arrays.fill(counts, 0);

        for (int i = 0; i < 1000; i++) {
            BlockPos pos = setup.getRandomPosition(startX + 9, startZ, 2.0);

            int ix = pos.getX() - startX;
            int iz = pos.getZ() - startZ;
            int idx = iz * width + ix;

            counts[idx]++;
        }

        var expected = new int[] {
                12,  4,  8,  9,  9, 14, 14, 27, 37, 38,
                 6,  3,  6,  6,  8, 12, 20, 42, 36, 35,
                 3, 11,  9,  5,  5, 11, 16, 21, 21, 32,
                11,  3,  3,  4, 10,  9,  5, 14, 20, 23,
                 4,  6,  6,  7,  5,  8,  8, 11, 16, 20,
                 4, 10,  3,  8,  9,  6,  8,  9,  8,  5,
                 6,  8,  6,  7,  6,  9, 13,  5, 10,  5,
                 8,  8,  5, 11, 10,  6,  5,  7,  5,  2,
                 6,  8,  5,  1,  8,  4,  4,  8,  9,  7,
                 3,  9,  5,  6,  5,  5,  4,  6,  6,  6
        };

        assertArrayEquals(expected, counts);
    }

    @SuppressWarnings("unused")
    private static void printMatrix(int[] matrix, int height, int width) {
        StringBuilder builder = new StringBuilder();

        for (int z = 0; z < height; z++) {
            builder.setLength(0);

            for (int x = 0; x < width; x++) {
                if (!builder.isEmpty()) builder.append(" ");
                builder.append("%02d".formatted(matrix[z * width + x]));
            }

            System.out.println(builder);
        }
    }
}
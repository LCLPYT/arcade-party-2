package work.lclpnet.ap2.impl.util;

import java.util.Random;

public class ArrayUtil {

    private ArrayUtil() {}

    public static void shuffle(int[] array, Random random) {
        // Fisher-Yates shuffle
        for (int i = array.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }
}

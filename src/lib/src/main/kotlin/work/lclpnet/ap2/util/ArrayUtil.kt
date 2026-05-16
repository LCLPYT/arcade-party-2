package work.lclpnet.ap2.util

import java.util.Random

fun shuffle(array: IntArray, random: Random) {
    // Fisher-Yates shuffle
    for (i in array.size - 1 downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = array[i]
        array[i] = array[j]
        array[j] = tmp
    }
}
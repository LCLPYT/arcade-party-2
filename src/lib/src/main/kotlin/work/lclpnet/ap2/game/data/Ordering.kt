package work.lclpnet.ap2.game.data

import java.util.function.ToDoubleFunction
import java.util.function.ToIntFunction
import java.util.stream.DoubleStream
import java.util.stream.IntStream

enum class Ordering {
    DESCENDING,
    ASCENDING;

    fun best(stream: IntStream): Int? {
        return (when (this) {
            DESCENDING -> stream.max()
            ASCENDING -> stream.min()
        }).stream().boxed().findAny().orElse(null)
    }

    fun best(stream: DoubleStream): Double? {
        return (when (this) {
            DESCENDING -> stream.max()
            ASCENDING -> stream.min()
        }).stream().boxed().findAny().orElse(null)
    }

    fun opposite(): Ordering {
        return when (this) {
            DESCENDING -> ASCENDING
            ASCENDING -> DESCENDING
        }
    }

    fun <T> orderInt(keyExtractor: ToIntFunction<T>): Comparator<T> {
        val comparator = Comparator.comparingInt(keyExtractor)

        return when (this) {
            DESCENDING -> comparator.reversed()
            ASCENDING -> comparator
        }
    }

    fun <T> orderDouble(keyExtractor: ToDoubleFunction<T>): Comparator<T> {
        val comparator = Comparator.comparingDouble(keyExtractor)

        return when (this) {
            DESCENDING -> comparator.reversed()
            ASCENDING -> comparator
        }
    }
}

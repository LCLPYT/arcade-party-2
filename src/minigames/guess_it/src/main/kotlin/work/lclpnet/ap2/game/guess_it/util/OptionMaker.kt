package work.lclpnet.ap2.game.guess_it.util

import net.minecraft.core.IdMap
import java.util.*

object OptionMaker {
    fun <T : Any> createOptions(pool: Set<T>, optionCount: Int, random: Random): List<T> {
        return createOptions(pool.toList(), optionCount, random)
    }

    fun <T : Any> createOptions(pool: List<T>, optionCount: Int, random: Random): List<T> {
        return createOptions(pool.size, optionCount, random) { index -> pool.get(index) }
    }

    fun <T : Any> createOptions(pool: IdMap<T>, optionCount: Int, random: Random): List<T> {
        return createOptions(pool.size(), optionCount, random) { id -> pool.byIdOrThrow(id) }
    }

    private fun <T> createOptions(
        poolSize: Int,
        optionCount: Int,
        random: Random,
        indexFunction: (Int) -> T
    ): List<T> {
        check(poolSize >= optionCount) { "There are less options available than requested" }

        val indices = mutableSetOf<Int>()

        while (indices.size < optionCount) {
            val i = random.nextInt(poolSize)
            indices.add(i)
        }

        return indices.map(indexFunction).toList()
    }
}

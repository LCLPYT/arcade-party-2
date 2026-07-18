package work.lclpnet.ap2.game.item

import com.google.common.collect.Iterables
import org.json.JSONObject
import work.lclpnet.gaco.ds.WeightedList

class SpecialItemRegistry : SpecialItemRegistrar {

    private val items = HashMap<String, Entry>()

    override fun register(item: SpecialItem, chance: Float): SpecialItemRegistrar {
        require(chance >= 0) { "Chance cannot be negative" }

        items[item.id] = Entry(item, chance)

        return this
    }

    fun weightedItems(overrides: JSONObject): WeightedList<SpecialItem> {
        val weighted = WeightedList<SpecialItem>(items.size)

        for ((key, entry) in items) {
            var chance = overrides.optFloat(key)

            if (chance.isNaN()) {
                chance = entry.chance
            }

            if (chance > 0) {
                weighted.add(entry.item, entry.chance)
            }
        }

        return weighted
    }

    operator fun get(id: String): SpecialItem? =
        items.getOrDefault(id, null)?.item

    fun entries(): Iterable<SpecialItem> =
        Iterables.transform(items.values, Entry::item)

    private data class Entry(
        val item: SpecialItem,
        val chance: Float,
    )
}

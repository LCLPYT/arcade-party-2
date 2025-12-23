package work.lclpnet.ap2.util.loot

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import work.lclpnet.gaco.ds.WeightedList

data class LootTable(val entries: List<LootTableEntry>) {

    fun loadInto(list: WeightedList<LootEntry>) {
        for ((entry, weight) in entries) {
            list.add(entry, weight)
        }
    }

    companion object {
        val CODEC: Codec<LootTable> = RecordCodecBuilder.create { instance ->
            instance.group(
                LootTableEntry.CODEC.listOf().fieldOf("entries").forGetter { it.entries }
            ).apply(instance) { entries ->
                LootTable(entries)
            }
        }
    }
}
package work.lclpnet.ap2.util.loot

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

data class LootTableEntry(val entry: LootEntry, val weight: Float) {

    companion object {
        val CODEC: Codec<LootTableEntry> = RecordCodecBuilder.create { instance ->
            instance.group(
                LootEntry.CODEC.fieldOf("entry").forGetter { it.entry },
                Codec.FLOAT.fieldOf("weight").forGetter { it.weight },
            ).apply(instance) { entry, weight ->
                LootTableEntry(entry, weight)
            }
        }
    }
}
package work.lclpnet.ap2.util.loot

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.impl.util.CodecUtil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class LootEntry(val itemStack: ItemStack, val minCount: Int = 1, val maxCount: Int = 1) {

    fun generateItemStack(): ItemStack {
        val count = Random.Default.nextInt(minCount, maxCount+1)
        return itemStack.copyWithCount(count)
    }

    companion object {
        val CODEC: Codec<LootEntry> = RecordCodecBuilder.create { instance ->
            instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter { it.itemStack },
                CodecUtil.POSITIVE_INT.fieldOf("min").orElse(1).forGetter { it.minCount },
                CodecUtil.POSITIVE_INT.fieldOf("max").orElse(1).forGetter { it.maxCount },
            ).apply(instance) { stack, i, j ->
                LootEntry(stack, min(i, j), max(i, j))
            }
        }
    }
}
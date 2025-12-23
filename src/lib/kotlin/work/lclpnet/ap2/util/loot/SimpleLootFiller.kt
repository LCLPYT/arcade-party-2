package work.lclpnet.ap2.util.loot

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import work.lclpnet.gaco.ds.WeightedList
import kotlin.random.Random
import kotlin.random.asJavaRandom

class SimpleLootFiller(
    val minItems: Int,
    val maxItems: Int,
    val loot: WeightedList<LootEntry>,
) : LootFiller {

    override fun fill(
        pos: BlockPos,
        level: ServerLevel,
        container: Container
    ) {
        container.clearContent()

        val invSize = container.containerSize
        val maxSlotsToFill = maxItems.coerceAtMost(invSize)
        val slotsToFill = Random.nextInt(minItems, maxSlotsToFill + 1)

        val availableSlots = (0 ..< invSize).toMutableList()

        repeat(slotsToFill) {
            val slot = availableSlots.removeAt(Random.nextInt(availableSlots.size))
            val entry = loot.getRandomElement(Random.asJavaRandom())

            if (entry != null) {
                container.setItem(slot, entry.generateItemStack())
            }
        }
    }
}
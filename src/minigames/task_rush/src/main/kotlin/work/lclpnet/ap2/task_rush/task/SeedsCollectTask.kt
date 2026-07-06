package work.lclpnet.ap2.task_rush.task

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.time.Duration.Companion.seconds

object SeedsCollectTask : InventoryCountTask("seeds_collect", "score.seeds_collected", 30.seconds) {

    override fun matches(stack: ItemStack) = stack.item == Items.WHEAT_SEEDS
}

package work.lclpnet.ap2.task_rush.task

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import kotlin.time.Duration.Companion.seconds

object FoodCollectTask : InventoryCountTask("food_collect", "score.food_collected", 30.seconds) {

    override fun matches(stack: ItemStack) = stack.has(DataComponents.CONSUMABLE)
}

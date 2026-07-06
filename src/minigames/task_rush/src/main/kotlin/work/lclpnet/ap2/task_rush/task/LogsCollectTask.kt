package work.lclpnet.ap2.task_rush.task

import net.minecraft.tags.ItemTags
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.ext.mc.isIn

object LogsCollectTask : InventoryCountTask("logs_collect", "score.logs_collected") {

    override fun matches(stack: ItemStack) = stack.isIn(ItemTags.LOGS)
}

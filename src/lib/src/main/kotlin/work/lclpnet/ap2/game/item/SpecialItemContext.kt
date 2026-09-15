package work.lclpnet.ap2.game.item

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations

interface SpecialItemContext {

    val scheduler: TaskScheduler

    val translations: Translations

    fun removeSpecialItem(player: ServerPlayer, item: SpecialItem)

    fun isSpecialItem(stack: ItemStack, item: SpecialItem): Boolean

    fun hasSpecialItem(player: ServerPlayer, item: SpecialItem?): Boolean
}

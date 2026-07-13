package work.lclpnet.ap2.task_rush

import net.minecraft.core.RegistryAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.GameStartContext
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameFactory

class TaskRushMiniGame : MiniGame {
    override val id = ApConstants.identifier("task_rush")
    override val type = GameType.FFA
    override val author = ApConstants.PERSON_LCLP
    override val usesMaps = false
    override fun getIcon(manager: RegistryAccess): ItemStack = ItemStack(Items.WRITABLE_BOOK)
    override fun canBeFinale(context: GameStartContext): Boolean = false
    override fun canBePlayed(context: GameStartContext): Boolean = true
    override fun createFactory(): MiniGameFactory = TaskRushFactory()
}

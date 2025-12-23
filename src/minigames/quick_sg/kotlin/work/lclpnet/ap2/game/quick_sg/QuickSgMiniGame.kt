package work.lclpnet.ap2.game.quick_sg

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.RegistryAccess
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.game.*

class QuickSgMiniGame : MiniGame {
    override fun canBeFinale(context: GameStartContext) = true
    override fun canBePlayed(context: GameStartContext) = true
    override fun getId() = ApConstants.identifier("quick_sg")
    override fun getType() = GameType.FFA
    override fun getAuthor() = ApConstants.PERSON_LCLP
    override fun getIcon(manager: RegistryAccess) = ItemStack(Items.IRON_SWORD)
    override fun createInstance(gameHandle: MiniGameHandle) = QuickSgInstance(gameHandle)
}
